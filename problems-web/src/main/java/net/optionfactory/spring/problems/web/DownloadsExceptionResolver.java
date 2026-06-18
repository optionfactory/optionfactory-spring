package net.optionfactory.spring.problems.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

public class DownloadsExceptionResolver implements HandlerExceptionResolver {

    private final Logger logger = LoggerFactory.getLogger(DownloadsExceptionResolver.class);
    private final Map<HandlerMethod, Integer> methodToIsDownload = new ConcurrentHashMap<>();

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod hm)) {
            return null;
        }
        final var downloadStatusCode = methodToIsDownload.computeIfAbsent(hm, m -> {
            final var annotation = AnnotatedElementUtils.findMergedAnnotation(m.getMethod(), ErrorAsHttpStatusOnly.class);
            return (annotation != null) ? annotation.status() : null;
        });
        response.reset();
        if (downloadStatusCode != null) {
            response.setStatus(downloadStatusCode);
        } else {
            response.setStatus((ex instanceof RestClientException) ? HttpStatus.BAD_GATEWAY.value() : HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        logger.warn(String.format("unhandled exception while downloading in %s", hm), ex);
        return new ModelAndView();
    }

}
