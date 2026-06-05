package net.optionfactory.spring.problems.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path.Node;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.optionfactory.spring.problems.Failure;
import net.optionfactory.spring.problems.Problem;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.ClassUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import org.springframework.web.servlet.view.json.JacksonJsonView;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

/**
 * A custom exception resolver resolving Spring and Jackson2 exceptions thrown
 * from an HandlerMethod annotated with
 * &#64;{@link org.springframework.web.bind.annotation.ResponseBody} with a
 * MappingJackson2JsonView. Sample serialized form of the response is:  <code>
 * [
 *   {"type": "", "context": "fieldName", "reason": a field validation error", "details": null},
 *   {"type": "", "context": null, "reason": "a global error", "details": null},
 * ]
 * </code> Content-Type header is set to <code>application/failures+json</code>
 */
public class RestExceptionResolver extends DefaultHandlerExceptionResolver {

    private final Map<HandlerMethod, Boolean> methodToIsRest = new ConcurrentHashMap<>();
    private final JsonMapper mapper;
    private final List<FailureTransformer> transformers;
    private final MessageSource messageSource;

    public enum Options {
        INCLUDE_DETAILS, OMIT_DETAILS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RestExceptionResolver withDefaults(Options options, JsonMapper mapper) {
        final var builder = new Builder().withOptions(options);
        if (ClassUtils.isPresent("net.optionfactory.spring.upstream.errors.RestClientUpstreamException", RestExceptionResolver.class.getClassLoader())) {
            try {
                builder.withTransformer((FailureTransformer) Class.forName("net.optionfactory.spring.problems.web.upstream.UpstreamFailureTransformer")
                        .getDeclaredConstructor()
                        .newInstance());
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to instantiate UpstreamFailureTransformer", ex);
            }
        }
        return builder.build(mapper);
    }

    public static class Builder {

        private Options options = Options.INCLUDE_DETAILS;
        private final List<FailureTransformer> transformers = new ArrayList<>();
        private MessageSource messageSource;

        public Builder withMessageSource(MessageSource messageSource) {
            this.messageSource = messageSource;
            return this;
        }

        public Builder withOptions(Options options) {
            this.options = options;
            return this;
        }

        public Builder withTransformer(FailureTransformer t) {
            this.transformers.add(t);
            return this;
        }

        public RestExceptionResolver build(JsonMapper mapper) {
            final var fts = new ArrayList<>(transformers);
            if (options == Options.OMIT_DETAILS) {
                fts.add(new OmitDetails());
            }
            final MessageSource ms;
            if (messageSource != null) {
                ms = messageSource;
            } else {
                final var defaultMessageSource = new ResourceBundleMessageSource();
                defaultMessageSource.setBasename("optionfactory-problems-web");
                defaultMessageSource.setDefaultEncoding("UTF-8");
                ms = defaultMessageSource;
            }
            return new RestExceptionResolver(mapper, ms, fts);
        }

    }

    public RestExceptionResolver(JsonMapper mapper, MessageSource messageSource, List<FailureTransformer> transformers) {
        this.mapper = mapper;
        this.transformers = transformers;
        this.messageSource = messageSource;
    }

    private HttpStatusAndProblems handleMessageNotReadable(String requestUri, HttpMessageNotReadableException ex, Locale locale) {
        final Throwable cause = ex.getCause();
        return switch (ex.getCause()) {
            case UnrecognizedPropertyException inner -> {
                final var metadata = new ConcurrentHashMap<String, Object>();
                metadata.put("known", inner.getKnownPropertyIds());
                metadata.put("in", inner.getReferringClass().getSimpleName());

                final var reason = messageSource.getMessage("error.unrecognized_field", null, "Unrecognized field", locale);
                final var problem = Problem.of("UNRECOGNIZED_PROPERTY", inner.getPropertyName(), reason, metadata);
                logger.debug(String.format("Unrecognized property at %s: %s", requestUri, problem));
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(problem));
            }
            case JacksonException inner -> {
                final var path = inner.getPath().stream().map(p -> p.getPropertyName()).collect(Collectors.joining("."));
                final Problem problem;
                if (path.isEmpty()) {
                    final var details = new ConcurrentHashMap<String, Object>();
                    details.put("location", inner.getLocation());
                    details.put("message", cause.getMessage());
                    final String reason = messageSource.getMessage("error.unparseable_message", null, "Unpearsable message", locale);
                    problem = Problem.of("UNPARSEABLE_MESSAGE", Problem.NO_CONTEXT, reason, details);
                } else {
                    final String reason = messageSource.getMessage("error.invalid_format", null, "Invalid format", locale);
                    problem = Problem.of("INVALID_FORMAT", path, reason, inner.getMessage());
                }
                logger.debug(String.format("Invalid format at %s: %s", requestUri, problem));
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(problem));
            }
            case null, default -> {
                final String reason = messageSource.getMessage("error.message_not_readable", null, "Message not readable", locale);
                final Problem problem = Problem.of("MESSAGE_NOT_READABLE", Problem.NO_CONTEXT, reason, cause != null ? cause.getMessage() : ex.getMessage());
                logger.debug(String.format("Unreadable message at %s: %s", requestUri, problem));
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(problem));
            }
        };
    }

    protected HttpStatusAndProblems toStatusAndErrors(HttpServletRequest request, HttpServletResponse response, HandlerMethod hm, Exception ex) {
        final String requestUri = request.getRequestURI();
        final var locale = LocaleContextHolder.getLocale();
        return switch (ex) {
            case HttpMessageNotReadableException inner -> {
                yield handleMessageNotReadable(requestUri, inner, locale);
            }
            case HandlerMethodValidationException hmve -> {
                final var failures = new ArrayList<Problem>();
                //NOTE: we are relying on jakarta validation translation so error.getDefaultMessage() is localized already
                for (final var result : hmve.getParameterValidationResults()) {
                    final var param = result.getMethodParameter();
                    final Object containerKey = result.getContainerIndex() != null ? result.getContainerIndex() : result.getContainerKey();
                    final String prefix = containerKey != null ? containerKey.toString() : "";

                    if (result instanceof org.springframework.validation.method.ParameterErrors pe) {
                        pe.getGlobalErrors().forEach(error -> failures.add(RestExceptionResolver.objectErrorToProblem(error)));
                        pe.getFieldErrors().forEach(error -> {
                            final String path = prefix.isEmpty() ? error.getField() : prefix + "." + error.getField();
                            failures.add(Problem.of("FIELD_ERROR", path, error.getDefaultMessage(), null));
                        });
                    } else {
                        final boolean isRequestBody = param.hasParameterAnnotation(RequestBody.class);
                        final String path = !prefix.isEmpty() ? prefix : (isRequestBody ? null : param.getParameterName());
                        result.getResolvableErrors().forEach(error -> failures.add(path == null
                                ? Problem.of("OBJECT_ERROR", null, error.getDefaultMessage(), null)
                                : Problem.of("FIELD_ERROR", path, error.getDefaultMessage(), null)
                        ));
                    }
                }

                logger.debug(String.format("Handler method validation failures at %s: %s", requestUri, failures));
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, failures);
            }
            case BindException be -> {
                final var globalFailures = be.getGlobalErrors().stream().map(RestExceptionResolver::objectErrorToProblem);
                final var fieldFailures = be.getFieldErrors().stream().map(RestExceptionResolver::fieldErrorToProblem);
                final var failures = Stream.concat(globalFailures, fieldFailures).toList();
                logger.debug(String.format("Binding failure at %s: %s", requestUri, failures));
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, failures);
            }
            case ConstraintViolationException cve -> {
                final var requestBodyParams = hm == null ? Set.<String>of() : Stream.of(hm.getMethodParameters())
                        .filter(p -> p.hasParameterAnnotation(RequestBody.class))
                        .map(MethodParameter::getParameterName)
                        .collect(Collectors.toSet());

                final var fieldFailures = cve.getConstraintViolations().stream()
                        .map(cv -> constraintViolationToProblem(cv, requestBodyParams));
                final var failures = fieldFailures.toList();
                logger.debug(String.format("Constraint violations at %s: %s", requestUri, failures));
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, failures);
            }
            case MissingServletRequestParameterException msrpe -> {
                final String reason = messageSource.getMessage("error.missing_parameter", null, "Parameter is missing", locale);
                final Problem problem = Problem.of("FIELD_ERROR", msrpe.getParameterName(), reason, Problem.NO_DETAILS);
                logger.debug(String.format("Missing servlet RequestParameter at %s: %s", requestUri, problem));
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(problem));
            }
            case MethodArgumentTypeMismatchException matme -> {
                // Handles type errors in path variables (Es. not-numeric string when expecting an int)
                final var parameterName = matme.getParameter().getParameterName();
                final var parameterType = matme.getParameter().getParameterType().toGenericString();
                final var value = matme.getValue();
                final var sourceType = value == null ? "null" : value.getClass().toGenericString();
                final var failures = List.of(Problem.of("CONVERSION_ERROR", parameterName, "Conversion error", String.format("Failed to convert value of type '%s' to '%s'.", sourceType, parameterType)));
                logger.debug(String.format("Conversion error for argument %s expected type %s found type %s at %s: %s", parameterName, parameterType, sourceType, requestUri, failures));
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, failures);
            }
            case MissingServletRequestPartException msrpe -> {
                final var problem = Problem.of("FIELD_ERROR", msrpe.getRequestPartName(), "Required request part is not present", Problem.NO_DETAILS);
                logger.debug(String.format("Missing required part %s of multipart request: %s", msrpe.getRequestPartName(), requestUri));
                yield new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(problem));
            }
            case ResponseStatusException rse -> {
                final var problem = Problem.of(HttpStatus.resolve(rse.getStatusCode().value()).name(), null, rse.getReason(), Problem.NO_DETAILS);
                logger.debug(String.format("ResponseStatusException at %s: %s", requestUri, problem));
                yield new HttpStatusAndProblems(HttpStatus.resolve(rse.getStatusCode().value()), List.of(problem));
            }
            case Failure failure -> {
                logger.debug(String.format("Failure at %s", requestUri), failure);
                yield new HttpStatusAndProblems(annotatedStatusOr(failure, HttpStatus.BAD_REQUEST), failure.problems);
            }
            case RestClientException rce -> {
                final var problem = Problem.of("UPSTREAM_ERROR", null, "upstream failure", rce.getMessage());
                logger.warn(String.format("Upstream error %s: %s", requestUri, rce.getMessage()), rce);
                yield new HttpStatusAndProblems(HttpStatus.BAD_GATEWAY, List.of(problem));
            }
            case AccessDeniedException ade -> {
                final var problem = Problem.of("FORBIDDEN", null, null, ade.getMessage());
                logger.debug(String.format("Access denied at %s: %s", requestUri, problem));
                yield new HttpStatusAndProblems(annotatedStatusOr(ade, HttpStatus.FORBIDDEN), List.of(problem));
            }
            default -> {
                if (null != super.doResolveException(request, new SendErrorToSetStatusHttpServletResponse(response), hm, ex)) {
                    if (request.getAttribute("javax.servlet.error.exception") != null) {
                        logger.warn(String.format("got an internal error from spring at %s", requestUri), ex);
                    }
                    final HttpStatus currentStatus = HttpStatus.valueOf(response.getStatus());
                    logger.warn(String.format("got an unexpected error while processing request at %s", requestUri), ex);
                    yield new HttpStatusAndProblems(annotatedStatusOr(ex, currentStatus), List.of(Problem.of("INTERNAL_ERROR", null, null, ex.getMessage())));
                }
                logger.error(String.format("got an unexpected error while processing request at %s", requestUri), ex);
                yield new HttpStatusAndProblems(annotatedStatusOr(ex, HttpStatus.INTERNAL_SERVER_ERROR), List.of(Problem.of("UNEXPECTED_PROBLEM", null, null, ex.getMessage())));
            }
        };
    }

    private HttpStatus annotatedStatusOr(Exception ex, HttpStatus defaultValue) {
        if (ex == null) {
            return defaultValue;
        }
        final var rs = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        return rs == null ? defaultValue : rs.value();
    }

    @Override
    protected boolean shouldApplyTo(HttpServletRequest request, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        return super.shouldApplyTo(request, handler) && methodToIsRest.computeIfAbsent(handlerMethod, m -> {
            return m.hasMethodAnnotation(ResponseBody.class) || AnnotatedElementUtils.hasAnnotation(m.getBeanType(), ResponseBody.class);
        });
    }

    @Override
    protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        final var hm = (HandlerMethod) handler;
        final var statusAndErrors = toStatusAndErrors(request, response, hm, ex);

        var transformed = new HttpStatusAndProblems(statusAndErrors.status(), statusAndErrors.problems());

        for (final var failureTansformer : transformers) {
            transformed = failureTansformer.transform(transformed, request, response, hm, ex);
        }

        response.setStatus(transformed.status.value());

        final var view = new JacksonJsonView(mapper);
        view.setExtractValueFromSingleKeyModel(true);
        view.setContentType("application/failures+json");
        return new ModelAndView(view, "errors", transformed.problems());
    }

    public static record HttpStatusAndProblems(HttpStatusCode status, List<Problem> problems) {

    }

    private static Problem constraintViolationToProblem(ConstraintViolation<?> error, Set<String> requestBodyParams) {
        final var nodes = StreamSupport.stream(error.getPropertyPath().spliterator(), false)
                .toList();

        final var paramName = nodes.stream()
                .filter(node -> node.getKind() == ElementKind.PARAMETER)
                .map(Node::getName)
                .findFirst()
                .orElse(null);

        final var isRequestBody = paramName != null && requestBodyParams.contains(paramName);

        final var path = nodes.stream()
                .filter(node -> node.getKind() != ElementKind.METHOD)
                .filter(node -> !(node.getKind() == ElementKind.PARAMETER && isRequestBody))
                .map(node -> {
                    String name = node.getName();
                    if (name != null && name.startsWith("<") && name.endsWith(">")) {
                        name = "";
                    }

                    if (node.getIndex() != null) {
                        return node.getIndex() + (name != null && !name.isEmpty() ? "." + name : "");
                    }
                    return name;
                })
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining("."));

        return path.isEmpty()
                ? Problem.of("OBJECT_ERROR", null, error.getMessage(), null)
                : Problem.of("FIELD_ERROR", path, error.getMessage(), null);
    }

    private static Problem fieldErrorToProblem(FieldError error) {
        return Problem.of("FIELD_ERROR", error.getField(), error.getDefaultMessage(), null);
    }

    private static Problem objectErrorToProblem(ObjectError error) {
        return Problem.of("OBJECT_ERROR", null, error.getDefaultMessage(), null);
    }

    public static class SendErrorToSetStatusHttpServletResponse extends HttpServletResponseWrapper {

        private final HttpServletResponse inner;

        public SendErrorToSetStatusHttpServletResponse(HttpServletResponse inner) {
            super(inner);
            this.inner = inner;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            inner.setStatus(sc);
        }

        @Override
        public void sendError(int sc) throws IOException {
            inner.setStatus(sc);
        }
    }
}
