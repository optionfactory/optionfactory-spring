package net.optionfactory.spring.problems.web;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/// Built in: spring mvc's own exceptions — an unreadable body, a failed binding or method validation,
/// a missing or mistyped parameter or part, a `ResponseStatusException` — and a failed call through
/// spring's http clients.
public class SpringWebProblemsModule implements ProblemsModule {

    private static final Logger logger = LoggerFactory.getLogger(SpringWebProblemsModule.class);

    @Override
    public List<ExceptionClassifier> classifiers() {
        return List.of(SpringWebProblemsModule::classify);
    }

    private static @Nullable HttpStatusAndProblems classify(ExceptionClassifier.Context context, Exception ex) {
        return switch (ex) {
            case HttpMessageNotReadableException inner ->
                messageNotReadable(context, inner);
            case HandlerMethodValidationException hmve -> {
                final var failures = new ArrayList<Problem>();
                //NOTE: we are relying on jakarta validation translation so error.getDefaultMessage() is localized already
                for (final var result : hmve.getParameterValidationResults()) {
                    final var param = result.getMethodParameter();
                    final Object containerKey = result.getContainerIndex() != null ? result.getContainerIndex() : result.getContainerKey();
                    final String prefix = containerKey != null ? containerKey.toString() : "";

                    if (result instanceof ParameterErrors pe) {
                        pe.getGlobalErrors().forEach(error -> failures.add(objectErrorToProblem(error)));
                        pe.getFieldErrors().forEach(error -> {
                            final String field = error.getField();
                            final String path = prefix.isEmpty() ? field : prefix + "." + field;
                            failures.add(Problem.of(Problem.TYPE_FIELD_ERROR, toDottedPath(path), error.getDefaultMessage(), null));
                        });
                    } else {
                        final boolean isRequestBody = param.hasParameterAnnotation(RequestBody.class);
                        final String path = !prefix.isEmpty() ? prefix : (isRequestBody ? null : requestName(param));
                        result.getResolvableErrors().forEach(error -> failures.add(path == null
                                ? Problem.of(Problem.TYPE_OBJECT_ERROR, null, error.getDefaultMessage(), null)
                                : Problem.of(Problem.TYPE_FIELD_ERROR, path, error.getDefaultMessage(), null)
                        ));
                    }
                }
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, failures);
            }
            case BindException be -> {
                // this handles MethodArgumentNotValidException too, the other exception thrown by unified validation
                final var globalFailures = be.getGlobalErrors().stream().map(SpringWebProblemsModule::objectErrorToProblem);
                final var fieldFailures = be.getFieldErrors().stream().map(SpringWebProblemsModule::fieldErrorToProblem);
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, Stream.concat(globalFailures, fieldFailures).toList());
            }
            case MissingServletRequestParameterException msrpe -> {
                final var reason = context.localized("error.missing_parameter", "Parameter is missing");
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(Problem.of(Problem.TYPE_FIELD_ERROR, msrpe.getParameterName(), reason, Problem.NO_DETAILS)));
            }
            case MethodArgumentTypeMismatchException matme -> {
                final var parameterName = matme.getName();
                final var parameterType = matme.getParameter().getParameterType().toGenericString();
                final var value = matme.getValue();
                final var sourceType = value == null ? "null" : value.getClass().toGenericString();
                final var reason = context.localized("error.invalid_format", "Invalid format");
                final var details = String.format("Failed to convert value of type '%s' to '%s'.", sourceType, parameterType);
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(Problem.of(Problem.TYPE_FIELD_ERROR, parameterName, reason, details)));
            }
            case MissingServletRequestPartException msrpe -> {
                final var reason = context.localized("error.missing_parameter", "Parameter is missing");
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(Problem.of(Problem.TYPE_FIELD_ERROR, msrpe.getRequestPartName(), reason, Problem.NO_DETAILS)));
            }
            case ResponseStatusException rse -> {
                final HttpStatusCode statusCode = rse.getStatusCode();
                final HttpStatus resolved = HttpStatus.resolve(statusCode.value());
                final var reason = context.localized(rse.getReason(), rse.getReason());
                final var type = resolved != null ? resolved.name() : String.format("HTTP_%d", statusCode.value());
                yield new HttpStatusAndProblems(statusCode, List.of(Problem.of(type, null, reason, Problem.NO_DETAILS)));
            }
            case RestClientException rce -> {
                logger.warn(String.format("Upstream error %s: %s", context.request().getRequestURI(), rce.getMessage()), rce);
                yield new HttpStatusAndProblems(HttpStatus.BAD_GATEWAY, List.of(Problem.upstream(null, "upstream failure", rce.getMessage())));
            }
            default ->
                null;
        };
    }

    private static HttpStatusAndProblems messageNotReadable(ExceptionClassifier.Context context, HttpMessageNotReadableException ex) {
        final Throwable cause = ex.getCause();
        return switch (ex.getCause()) {
            case UnrecognizedPropertyException inner -> {
                final var metadata = new ConcurrentHashMap<String, Object>();
                metadata.put("known", inner.getKnownPropertyIds());
                metadata.put("in", inner.getReferringClass().getSimpleName());
                final var reason = context.localized("error.unrecognized_field", "Unrecognized field");
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(Problem.request(inner.getPropertyName(), reason, metadata)));
            }
            case JacksonException inner -> {
                final var path = inner.getPath().stream()
                        .map(p -> p.getPropertyName() != null ? p.getPropertyName() : Integer.toString(p.getIndex()))
                        .collect(Collectors.joining("."));
                final Problem problem;
                if (path.isEmpty()) {
                    final var details = new ConcurrentHashMap<String, Object>();
                    details.put("location", inner.getLocation());
                    details.put("message", cause.getMessage());
                    problem = Problem.request(Problem.NO_CONTEXT, context.localized("error.unparseable_message", "Unparsable message"), details);
                } else {
                    problem = Problem.request(path, context.localized("error.invalid_format", "Invalid format"), inner.getMessage());
                }
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(problem));
            }
            case null, default -> {
                final var reason = context.localized("error.message_not_readable", "Message not readable");
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(Problem.request(Problem.NO_CONTEXT, reason, cause != null ? cause.getMessage() : ex.getMessage())));
            }
        };
    }

    /// The name the client sent a parameter under: the one declared on its `@RequestParam`,
    /// `@PathVariable`, `@RequestHeader`, `@CookieValue`, `@MatrixVariable` or `@RequestPart`, else the
    /// parameter's own.
    private static String requestName(MethodParameter param) {
        final var declared = Stream.of(
                named(param.getParameterAnnotation(RequestParam.class), RequestParam::name, RequestParam::value),
                named(param.getParameterAnnotation(PathVariable.class), PathVariable::name, PathVariable::value),
                named(param.getParameterAnnotation(RequestHeader.class), RequestHeader::name, RequestHeader::value),
                named(param.getParameterAnnotation(CookieValue.class), CookieValue::name, CookieValue::value),
                named(param.getParameterAnnotation(MatrixVariable.class), MatrixVariable::name, MatrixVariable::value),
                named(param.getParameterAnnotation(RequestPart.class), RequestPart::name, RequestPart::value)
        ).filter(name -> name != null && !name.isEmpty()).findFirst();
        return declared.orElseGet(() -> param.getParameterName() != null ? param.getParameterName() : "arg" + param.getParameterIndex());
    }

    private static <A> @Nullable String named(@Nullable A annotation, Function<A, String> name, Function<A, String> value) {
        if (annotation == null) {
            return null;
        }
        return !name.apply(annotation).isEmpty() ? name.apply(annotation) : value.apply(annotation);
    }

    private static String toDottedPath(String path) {
        return path.replaceAll("\\[(\\d+)\\]", ".$1").replaceFirst("^\\.", "");
    }

    private static Problem fieldErrorToProblem(FieldError error) {
        return Problem.of(Problem.TYPE_FIELD_ERROR, toDottedPath(error.getField()), error.getDefaultMessage(), null);
    }

    private static Problem objectErrorToProblem(ObjectError error) {
        return Problem.of(Problem.TYPE_OBJECT_ERROR, null, error.getDefaultMessage(), null);
    }
}
