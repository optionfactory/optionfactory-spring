package net.optionfactory.problems.web;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import net.optionfactory.problems.Failure;
import net.optionfactory.problems.Problem;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

/**
 * A custom exception resolver resolving Spring and Jackson2 exceptions thrown
 * from an HandlerMethod annotated with {@link @ResponseBody} with a
 * MappingJackson2JsonView. Sample serialized form of the response is:  <code>
 * [
 *   {"type": "", "context": "fieldName", "reason": a field validation error", "details": null},
 *   {"type": "", "context": null, "reason": "a global error", "details": null},
 * ]
 * </code>
 */
public class RestExceptionResolver extends DefaultHandlerExceptionResolver {

    private final Map<HandlerMethod, Boolean> methodToIsRest = new ConcurrentHashMap<>();
    private final ContentNegotiationManager cn;
    private final LinkedHashMap<MediaType, Supplier<View>> mediaTypeToViewFactory;
    private final Options options;
    
    public enum Options {
        INCLUDE_DETAILS, OMIT_DETAILS;
    }
    

    public RestExceptionResolver(ContentNegotiationManager cn, LinkedHashMap<MediaType, Supplier<View>> mediaTypeToViewFactory, int order, Options options) {
        this.cn = cn;
        this.mediaTypeToViewFactory = mediaTypeToViewFactory;
        this.setOrder(order);
        this.options = options;
    }

    protected HttpStatusAndFailures toStatusAndErrors(HttpServletRequest request, HttpServletResponse response, HandlerMethod hm, Exception ex) {
        final String requestUri = request.getRequestURI();
        if (ex instanceof HttpMessageNotReadableException) {
            final Throwable cause = ex.getCause();
            if (cause instanceof UnrecognizedPropertyException) {
                final UnrecognizedPropertyException inner = (UnrecognizedPropertyException) cause;
                final Map<String, Object> metadata = new ConcurrentHashMap<>();
                metadata.put("known", inner.getKnownPropertyIds());
                metadata.put("in", inner.getReferringClass().getSimpleName());
                final Problem failure = Problem.of("UNRECOGNIZED_PROPERTY", inner.getPropertyName(), "Unrecognized field", metadata);
                logger.debug(String.format("Unrecognized property at %s: %s", requestUri, failure));
                return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, Arrays.asList(failure));
            }
            if (cause instanceof InvalidFormatException) {
                final InvalidFormatException inner = (InvalidFormatException) cause;
                final String path = inner.getPath().stream().map(p -> p.getFieldName()).collect(Collectors.joining("."));
                final Problem failure = Problem.of("INVALID_FORMAT", path, "Invalid format", inner.getMessage());
                logger.debug(String.format("Invalid format at %s: %s", requestUri, failure));
                return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, Arrays.asList(failure));
            }
            if (cause instanceof JsonMappingException) {
                final JsonMappingException inner = (JsonMappingException) cause;
                final String path = inner.getPath().stream().map(p -> p.getFieldName()).collect(Collectors.joining("."));
                final Problem failure = Problem.of("INVALID_FORMAT", path, "Invalid format", inner.getMessage());
                logger.debug(String.format("Json mapping exception at %s: %s", requestUri, failure));
                return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, Arrays.asList(failure));
            }
            if (cause instanceof JsonParseException) {
                final JsonParseException inner = (JsonParseException) cause;
                final Map<String, Object> details = new ConcurrentHashMap<>();
                details.put("location", inner.getLocation());
                details.put("message", cause.getMessage());
                final Problem failure = Problem.of("UNPARSEABLE_MESSAGE", Problem.NO_CONTEXT, "Unpearsable message", details);
                logger.debug(String.format("Unparseable message: %s", failure.toString()));
                return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, Arrays.asList(failure));
            }
            final Problem failure = Problem.of("MESSAGE_NOT_READABLE", Problem.NO_CONTEXT, "Message not readable", cause != null ? cause.getMessage() : ex.getMessage());
            logger.debug(String.format("Unreadable message at %s: %s", requestUri, failure));
            return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, Arrays.asList(failure));
        }
        if (ex instanceof BindException) {
            final BindException be = (BindException) ex;
            final Stream<Problem> globalFailures = be.getGlobalErrors().stream().map(RestExceptionResolver::objectErrorToProblem);
            final Stream<Problem> fieldFailures = be.getFieldErrors().stream().map(RestExceptionResolver::fieldErrorToProblem);
            final List<Problem> failures = Stream.concat(globalFailures, fieldFailures).collect(Collectors.toList());
            logger.debug(String.format("Binding failure at %s: %s", requestUri, failures));
            return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, failures);
        }
        if (ex instanceof MethodArgumentNotValidException) {
            final MethodArgumentNotValidException manve = (MethodArgumentNotValidException) ex;
            final Stream<Problem> globalFailures = manve.getBindingResult().getGlobalErrors().stream().map(RestExceptionResolver::objectErrorToProblem);
            final Stream<Problem> fieldFailures = manve.getBindingResult().getFieldErrors().stream().map(RestExceptionResolver::fieldErrorToProblem);
            final List<Problem> failures = Stream.concat(globalFailures, fieldFailures).collect(Collectors.toList());
            logger.debug(String.format("Invalid method argument at %s: %s", requestUri, failures));
            return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, failures);
        }
        if (ex instanceof MethodArgumentTypeMismatchException) { // Handles type errors in path variables (Es. not-numeric string when expecting an int)
            final MethodArgumentTypeMismatchException matme = (MethodArgumentTypeMismatchException) ex;
            final String parameterName = matme.getParameter().getParameterName();
            final String parameterType = matme.getParameter().getParameterType().toGenericString();
            final Object value = matme.getValue();
            final String sourceType = value == null ? "null" : value.getClass().toGenericString();
            final List<Problem> failures = Collections.singletonList(Problem.of("CONVERSION_ERROR", parameterName, "Conversion error", String.format("Failed to convert value of type '%s' to '%s'.", sourceType, parameterType)));
            logger.debug(String.format("Conversion error for argument %s expected type %s found type %s at %s: %s", parameterName, parameterType, sourceType, requestUri, failures));
            return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, failures);
        }
        if (ex instanceof MissingServletRequestPartException) { // Handles missing multipart request part
            final MissingServletRequestPartException msrpe = (MissingServletRequestPartException) ex;
            final Problem problem = Problem.of("FIELD_ERROR", msrpe.getRequestPartName(), "Required request part is not present", Problem.NO_DETAILS);
            logger.debug(String.format("Missing required part %s of multipart request: %s", msrpe.getRequestPartName(), requestUri));
            return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, Collections.singletonList(problem));
        }
        if (ex instanceof ResponseStatusException) {
            final ResponseStatusException rse = (ResponseStatusException) ex;
            final Problem problem = Problem.of(rse.getStatus().name(), null, null, rse.getReason());
            return new HttpStatusAndFailures(rse.getStatus(), Collections.singletonList(problem));
        }
        final ResponseStatus responseStatus = AnnotationUtils.findAnnotation(ex.getClass(), ResponseStatus.class);
        if (responseStatus != null) {
            if (ex instanceof Failure) {
                final Failure failure = (Failure) ex;
                logger.debug(String.format("Failure at %s", requestUri), failure);
                return new HttpStatusAndFailures(responseStatus.value(), failure.problems);
            }
            final String reason = responseStatus.reason().isEmpty() ? ex.getMessage() : responseStatus.reason();
            final Problem problem = Problem.of("GENERIC_PROBLEM", null, null, reason);
            logger.debug(String.format("Failure at %s: %s", requestUri, problem));
            return new HttpStatusAndFailures(responseStatus.value(), Collections.singletonList(problem));
        }
        if (ex instanceof Failure) {
            final Failure failure = (Failure) ex;
            logger.debug(String.format("Failure at %s", requestUri), failure);
            return new HttpStatusAndFailures(HttpStatus.BAD_REQUEST, failure.problems);
        }
        if (ex instanceof AccessDeniedException) {
            final Problem problem = Problem.of("FORBIDDEN", null, null, ex.getMessage());
            logger.debug(String.format("Access denied at %s: %s", requestUri, problem));
            return new HttpStatusAndFailures(HttpStatus.FORBIDDEN, Collections.singletonList(problem));
        }
        if (null != super.doResolveException(request, new SendErrorToSetStatusHttpServletResponse(response), hm, ex)) {
            if (request.getAttribute("javax.servlet.error.exception") != null) {
                logger.warn(String.format("got an internal error from spring at %s", requestUri), ex);
            }
            final HttpStatus currentStatus = HttpStatus.valueOf(response.getStatus());
            logger.warn(String.format("got an unexpected error while processing request at %s", requestUri), ex);
            return new HttpStatusAndFailures(currentStatus, Collections.singletonList(Problem.of("INTERNAL_ERROR", null, null, ex.getMessage())));
        }
        logger.error(String.format("got an unexpected error while processing request at %s", requestUri), ex);
        return new HttpStatusAndFailures(HttpStatus.INTERNAL_SERVER_ERROR, Collections.singletonList(Problem.of("UNEXPECTED_PROBLEM", null, null, ex.getMessage())));
    }

    @Override
    protected boolean shouldApplyTo(HttpServletRequest request, Object handler) {
        return super.shouldApplyTo(request, handler) && (handler == null || methodToIsRest.computeIfAbsent((HandlerMethod) handler, m -> {
            return m.hasMethodAnnotation(ResponseBody.class) || AnnotatedElementUtils.hasAnnotation(m.getBeanType(), ResponseBody.class);
        }));
    }

    @Override
    protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        final HandlerMethod hm = (HandlerMethod) handler;
        final HttpStatusAndFailures statusAndErrors = toStatusAndErrors(request, response, hm, ex);
        if(options == Options.OMIT_DETAILS){
            statusAndErrors.failures.forEach(p -> {
                p.details = null;
            });
        }
        response.setStatus(statusAndErrors.status.value());

        try {
            final List<MediaType> mts = cn.resolveMediaTypes(new ServletWebRequest(request));

            for (MediaType mt : mts) {
                for (Map.Entry<MediaType, Supplier<View>> mediaTypeAndViewFactory : mediaTypeToViewFactory.entrySet()) {
                    final MediaType mediaType = mediaTypeAndViewFactory.getKey();
                    final Supplier<View> viewFactory = mediaTypeAndViewFactory.getValue();
                    if (mt.isCompatibleWith(mediaType)) {
                        return new ModelAndView(viewFactory.get(), "errors", statusAndErrors.failures);
                    }
                }
            }
            final Supplier<View> firstSupplier = mediaTypeToViewFactory.values().iterator().next();
            return new ModelAndView(firstSupplier.get(), "errors", statusAndErrors.failures);
        } catch (HttpMediaTypeNotAcceptableException ex1) {
            final Supplier<View> firstSupplier = mediaTypeToViewFactory.values().iterator().next();
            return new ModelAndView(firstSupplier.get(), "errors", statusAndErrors.failures);
        }
    }

    public static class HttpStatusAndFailures {

        public final HttpStatus status;
        public final List<Problem> failures;

        public HttpStatusAndFailures(HttpStatus status, List<Problem> failures) {
            this.status = status;
            this.failures = failures;
        }

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
