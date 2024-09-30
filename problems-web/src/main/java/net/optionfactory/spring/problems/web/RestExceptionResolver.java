package net.optionfactory.spring.problems.web;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.optionfactory.spring.problems.Failure;
import net.optionfactory.spring.problems.Problem;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

/**
 * A custom exception resolver resolving Spring and Jackson2 exceptions thrown
 * from an HandlerMethod annotated with
 * &#64;{@link org.springframework.web.bind.annotation.ResponseBody} with a
 * MappingJackson2JsonView. Sample serialized form of the response is:  <code>
 * [
 *   {"type": "", "context": "fieldName", "reason": a field validation error", "details": null},
 *   {"type": "", "context": null, "reason": "a global error", "details": null},
 * ]
 * </code>
 */
public class RestExceptionResolver extends DefaultHandlerExceptionResolver {

    private final Map<HandlerMethod, Boolean> methodToIsRest = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final ProblemTransformer[] transformers;

    public enum Options {
        INCLUDE_DETAILS, OMIT_DETAILS;
    }

    public RestExceptionResolver(ObjectMapper mapper, Options options, ProblemTransformer... transformers) {
        this.mapper = mapper;
        this.transformers = options == Options.INCLUDE_DETAILS ? transformers : withOmitDetails(transformers);
    }

    private ProblemTransformer[] withOmitDetails(ProblemTransformer[] transformers) {
        final ProblemTransformer[] r = new ProblemTransformer[transformers.length + 1];
        System.arraycopy(transformers, 0, r, 0, transformers.length);
        r[transformers.length] = new OmitDetails();
        return r;
    }

    private HttpStatusAndFailures handleMessageNotReadable(String requestUri, HttpMessageNotReadableException ex) {
        final Throwable cause = ex.getCause();
        return switch (ex.getCause()) {
            case UnrecognizedPropertyException inner -> {
                final Map<String, Object> metadata = new ConcurrentHashMap<>();
                metadata.put("known", inner.getKnownPropertyIds());
                metadata.put("in", inner.getReferringClass().getSimpleName());
                final Problem failure = Problem.of("UNRECOGNIZED_PROPERTY", inner.getPropertyName(), "Unrecognized field", metadata);
                logger.debug(String.format("Unrecognized property at %s: %s", requestUri, failure));
                yield new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, List.of(failure));
            }
            case InvalidFormatException inner -> {
                final String path = inner.getPath().stream().map(p -> p.getFieldName()).collect(Collectors.joining("."));
                final Problem failure = Problem.of("INVALID_FORMAT", path, "Invalid format", inner.getMessage());
                logger.debug(String.format("Invalid format at %s: %s", requestUri, failure));
                yield new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, List.of(failure));
            }
            case JsonMappingException inner -> {
                final String path = inner.getPath().stream().map(p -> p.getFieldName()).collect(Collectors.joining("."));
                final Problem failure = Problem.of("INVALID_FORMAT", path, "Invalid format", inner.getMessage());
                logger.debug(String.format("Json mapping exception at %s: %s", requestUri, failure));
                yield new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, List.of(failure));
            }
            case JsonParseException inner -> {
                final Map<String, Object> details = new ConcurrentHashMap<>();
                details.put("location", inner.getLocation());
                details.put("message", cause.getMessage());
                final Problem failure = Problem.of("UNPARSEABLE_MESSAGE", Problem.NO_CONTEXT, "Unpearsable message", details);
                logger.debug(String.format("Unparseable message: %s", failure.toString()));
                yield new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, List.of(failure));
            }
            case null, default -> {
                final Problem failure = Problem.of("MESSAGE_NOT_READABLE", Problem.NO_CONTEXT, "Message not readable", cause != null ? cause.getMessage() : ex.getMessage());
                logger.debug(String.format("Unreadable message at %s: %s", requestUri, failure));
                yield new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, List.of(failure));
            }
        };
    }

    protected HttpStatusAndFailures toStatusAndErrors(HttpServletRequest request, HttpServletResponse response, HandlerMethod hm, Exception ex) {
        final String requestUri = request.getRequestURI();
        if (ex instanceof HttpMessageNotReadableException inner) {
            return handleMessageNotReadable(requestUri, inner);
        }
        if (ex instanceof BindException be) {
            final Stream<Problem> globalFailures = be.getGlobalErrors().stream().map(RestExceptionResolver::objectErrorToProblem);
            final Stream<Problem> fieldFailures = be.getFieldErrors().stream().map(RestExceptionResolver::fieldErrorToProblem);
            final List<Problem> failures = Stream.concat(globalFailures, fieldFailures).collect(Collectors.toList());
            logger.debug(String.format("Binding failure at %s: %s", requestUri, failures));
            return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, failures);
        }
        if (ex instanceof ConstraintViolationException cve) {
            final Stream<Problem> fieldFailures = cve.getConstraintViolations().stream().map(RestExceptionResolver::constraintViolationToProblem);
            final List<Problem> failures = fieldFailures.collect(Collectors.toList());
            logger.debug(String.format("Constraint violations at %s: %s", requestUri, failures));
            return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, failures);
        }
        if (ex instanceof MissingServletRequestParameterException msrpe) {
            final Problem problem = Problem.of("FIELD_ERROR", msrpe.getParameterName(), "Parameter is missing", Problem.NO_DETAILS);
            return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, Collections.singletonList(problem));
        }
        if (ex instanceof MethodArgumentTypeMismatchException matme) { // Handles type errors in path variables (Es. not-numeric string when expecting an int)
            final String parameterName = matme.getParameter().getParameterName();
            final String parameterType = matme.getParameter().getParameterType().toGenericString();
            final Object value = matme.getValue();
            final String sourceType = value == null ? "null" : value.getClass().toGenericString();
            final List<Problem> failures = Collections.singletonList(Problem.of("CONVERSION_ERROR", parameterName, "Conversion error", String.format("Failed to convert value of type '%s' to '%s'.", sourceType, parameterType)));
            logger.debug(String.format("Conversion error for argument %s expected type %s found type %s at %s: %s", parameterName, parameterType, sourceType, requestUri, failures));
            return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, failures);
        }
        if (ex instanceof MissingServletRequestPartException msrpe) { // Handles missing multipart request part
            final Problem problem = Problem.of("FIELD_ERROR", msrpe.getRequestPartName(), "Required request part is not present", Problem.NO_DETAILS);
            logger.debug(String.format("Missing required part %s of multipart request: %s", msrpe.getRequestPartName(), requestUri));
            return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, Collections.singletonList(problem));
        }
        if (ex instanceof ResponseStatusException rse) {
            final Problem problem = Problem.of(HttpStatus.resolve(rse.getStatusCode().value()).name(), null, rse.getReason(), Problem.NO_DETAILS);
            return new HttpStatusAndFailures(HttpStatus.resolve(rse.getStatusCode().value()), Collections.singletonList(problem));
        }
        final ResponseStatus responseStatus = AnnotationUtils.findAnnotation(ex.getClass(), ResponseStatus.class);
        if (responseStatus != null) {
            if (ex instanceof Failure failure) {
                logger.debug(String.format("Failure at %s", requestUri), failure);
                return new HttpStatusAndFailures(responseStatus.value(), failure.problems);
            }
            final String reason = responseStatus.reason().isEmpty() ? ex.getMessage() : responseStatus.reason();
            final Problem problem = Problem.of("GENERIC_PROBLEM", null, null, reason);
            logger.debug(String.format("Failure at %s: %s", requestUri, problem));
            return new HttpStatusAndFailures(responseStatus.value(), List.of(problem));
        }
        if (ex instanceof Failure failure) {
            logger.debug(String.format("Failure at %s", requestUri), failure);
            return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, failure.problems);
        }
        if (ex instanceof RestClientException) {
            final Problem problem = Problem.of("UPSTREAM_ERROR", null, "upstream failure", ex.getMessage());
            logger.warn(String.format("Upstream error %s: %s", requestUri, ex.getMessage()), ex);
            return new HttpStatusAndFailures(HttpStatus.BAD_GATEWAY, List.of(problem));
        }
        if (ex instanceof AccessDeniedException) {
            final Problem problem = Problem.of("FORBIDDEN", null, null, ex.getMessage());
            logger.debug(String.format("Access denied at %s: %s", requestUri, problem));
            return new HttpStatusAndFailures(HttpStatus.FORBIDDEN, List.of(problem));
        }
        if (null != super.doResolveException(request, new SendErrorToSetStatusHttpServletResponse(response), hm, ex)) {
            if (request.getAttribute("javax.servlet.error.exception") != null) {
                logger.warn(String.format("got an internal error from spring at %s", requestUri), ex);
            }
            final HttpStatus currentStatus = HttpStatus.valueOf(response.getStatus());
            logger.warn(String.format("got an unexpected error while processing request at %s", requestUri), ex);
            return new HttpStatusAndFailures(currentStatus, List.of(Problem.of("INTERNAL_ERROR", null, null, ex.getMessage())));
        }
        logger.error(String.format("got an unexpected error while processing request at %s", requestUri), ex);
        return new HttpStatusAndFailures(HttpStatus.INTERNAL_SERVER_ERROR, List.of(Problem.of("UNEXPECTED_PROBLEM", null, null, ex.getMessage())));
    }

    @Override
    protected boolean shouldApplyTo(HttpServletRequest request, Object handler) {
        if (handler instanceof HandlerMethod == false) {
            return false;
        }
        final var handlerMethod = (HandlerMethod) handler;
        return super.shouldApplyTo(request, handler) && methodToIsRest.computeIfAbsent(handlerMethod, m -> {
            return m.hasMethodAnnotation(ResponseBody.class) || AnnotatedElementUtils.hasAnnotation(m.getBeanType(), ResponseBody.class);
        });
    }

    @Override
    protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        final HandlerMethod hm = (HandlerMethod) handler;
        final HttpStatusAndFailures statusAndErrors = toStatusAndErrors(request, response, hm, ex);

        final var transformedFailures = statusAndErrors.failures
                .stream()
                .map(p -> applyAllTransformers(p, request, response, hm, ex))
                .collect(Collectors.toList());

        response.setStatus(statusAndErrors.status.value());

        final MappingJackson2JsonView view = new MappingJackson2JsonView();
        view.setExtractValueFromSingleKeyModel(true);
        view.setObjectMapper(mapper);
        view.setContentType("application/json;charset=UTF-8");
        return new ModelAndView(view, "errors", transformedFailures);
    }

    private Problem applyAllTransformers(Problem p, HttpServletRequest request, HttpServletResponse response, HandlerMethod handler, Exception ex) {
        for (ProblemTransformer pt : transformers) {
            p = pt.transform(p, request, response, handler, ex);
        }
        return p;
    }

    public static record HttpStatusAndFailures(HttpStatus status, List<Problem> failures) {

    }

    private static Problem constraintViolationToProblem(ConstraintViolation error) {
        final var path = StreamSupport.stream(error.getPropertyPath().spliterator(), false)
                .skip(1)
                .map(node -> node.getIndex() != null ? String.valueOf(node.getIndex()) : node.getName())
                .collect(Collectors.joining("."));
        return Problem.of("FIELD_ERROR", path, error.getMessage(), null);
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
