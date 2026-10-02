package net.optionfactory.spring.problems.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.server.ResponseStatusException;

/// Answers a failed download with a bare status and no body.
///
/// A download cannot carry a problem document or an error page: the client expects a file, and
/// may already have been told its name with a `Content-Disposition` header. So when such a handler
/// fails before the response is committed, this resolver resets the response, discarding the
/// headers and anything buffered, and sets the status alone: the one of [BinaryResponseErrorStatus]
/// when the handler or its class declares one, the method's winning, else the status of a `ResponseStatusException`, `502` for a
/// `RestClientException`, and `500` for anything else. When the response is already committed it
/// is left as it is. Either way the exception is logged at `WARN` and the resolver answers it.
///
/// A handler method is taken for a download, judging once per method, when it:
///
/// 1. is annotated with [BinaryResponseErrorStatus], or belongs to a class that is;
/// 2. returns a `Resource`, a `StreamingResponseBody` or a `byte[]`;
/// 3. returns an `HttpEntity` or a `ResponseEntity` of a `Resource` or a `byte[]`;
/// 4. returns `void` and takes the `HttpServletResponse` or an `OutputStream` to write to;
/// 5. declares, in its `@RequestMapping`, that it `produces` `application/octet-stream`,
///    `application/pdf` or `application/zip`, which catches the downloads written by a message
///    converter.
///
/// Any other handler is also treated as a download when the response it failed on already has a
/// `Content-Disposition` header. Handlers that are not `HandlerMethod`s are declined.
///
/// Installed with [ExceptionResolvers#binaries()], behind [RestExceptionResolver]: the downloads
/// of `@ResponseBody` handlers are answered by the latter.
public class BinaryResponseExceptionResolver implements HandlerExceptionResolver {

    private final Logger logger = LoggerFactory.getLogger(BinaryResponseExceptionResolver.class);

    private record EndpointConfig(boolean isDownload, Integer status) {

    }
    private static final Set<String> DETECTED_DOWNLOAD_MEDIA_TYPES = Set.of(
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            "application/pdf",
            "application/zip"
    );

    private final Map<HandlerMethod, EndpointConfig> methodCache = new ConcurrentHashMap<>();

    /// @param request the current request
    /// @param response the current response, reset when the download failed before committing it
    /// @param handler the handler that threw
    /// @param ex the exception
    /// @return an empty `ModelAndView` for a download, meaning answered with no body, `null` to
    ///         decline anything else
    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod hm)) {
            return null;
        }
        final var config = methodCache.computeIfAbsent(hm, this::inspectEndpoint);
        if (!config.isDownload() && !response.containsHeader(HttpHeaders.CONTENT_DISPOSITION)) {
            return null;
        }
        if (response.isCommitted()) {
            logger.warn(String.format("unhandled exception while downloading in %s but the response was alreay committed. HTTP status code has been left unchanged", hm), ex);
            return new ModelAndView();
        }
        response.reset();
        response.setStatus(statusCode(config.status(), ex));
        logger.warn(String.format("unhandled exception while downloading in %s", hm), ex);
        return new ModelAndView();
    }
    
    private int statusCode(Integer configStatus, Exception ex) {
        if(configStatus != null){
            return configStatus;
        }
        return switch(ex){
            case ResponseStatusException rse -> rse.getStatusCode().value();
            case RestClientException rce -> HttpStatus.BAD_GATEWAY.value();
            default-> HttpStatus.INTERNAL_SERVER_ERROR.value();
        };
    }

    private EndpointConfig inspectEndpoint(HandlerMethod hm) {
        final var methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(hm.getMethod(), BinaryResponseErrorStatus.class);
        final var annotation = methodAnnotation != null ? methodAnnotation : AnnotatedElementUtils.findMergedAnnotation(hm.getBeanType(), BinaryResponseErrorStatus.class);
        if (annotation != null) {
            return new EndpointConfig(true, annotation.value().value());
        }

        final var returnType = ResolvableType.forMethodReturnType(hm.getMethod());
        final var rawClass = returnType.resolve(Object.class);

        if (rawClass != null && (Resource.class.isAssignableFrom(rawClass) || StreamingResponseBody.class.isAssignableFrom(rawClass) || byte[].class.isAssignableFrom(rawClass))) {
            return new EndpointConfig(true, null);
        }

        if (rawClass != null && (HttpEntity.class.isAssignableFrom(rawClass) || ResponseEntity.class.isAssignableFrom(rawClass))) {
            final var bodyType = returnType.getGeneric(0).resolve(Object.class);
            if (bodyType != null && (Resource.class.isAssignableFrom(bodyType) || byte[].class.isAssignableFrom(bodyType))) {
                return new EndpointConfig(true, null);
            }
        }

        if (void.class.equals(rawClass)) {
            for (final var param : hm.getMethodParameters()) {
                final var paramType = param.getParameterType();
                if (HttpServletResponse.class.isAssignableFrom(paramType) || OutputStream.class.isAssignableFrom(paramType)) {
                    return new EndpointConfig(true, null);
                }
            }
        }

        final var reqMapping = AnnotatedElementUtils.findMergedAnnotation(hm.getMethod(), RequestMapping.class);
        if (reqMapping != null) {
            for (final var produces : reqMapping.produces()) {
                if (DETECTED_DOWNLOAD_MEDIA_TYPES.stream().anyMatch(produces::contains)) {
                    return new EndpointConfig(true, null);
                }
            }
        }

        return new EndpointConfig(false, null);
    }
}
