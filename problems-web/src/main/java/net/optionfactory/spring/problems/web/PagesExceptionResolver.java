package net.optionfactory.spring.problems.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

public class PagesExceptionResolver implements HandlerExceptionResolver {

    private final Logger logger = LoggerFactory.getLogger(PagesExceptionResolver.class);
    private final ModelAndView defaultMav;
    private final Map<Class<? extends Exception>, ModelViewStatus> exceptionToView;

    public PagesExceptionResolver(ModelAndView defaultMav, Map<Class<? extends Exception>, ModelViewStatus> exceptionToView) {
        this.defaultMav = defaultMav;
        this.exceptionToView = exceptionToView;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (ex instanceof AccessDeniedException) {
            // Explicitly do nothing, just return null to trigger the next resolvers (notably the ones registered by spring security)
            return null;
        }
        for (var entry : exceptionToView.entrySet()) {
            if (entry.getKey().isInstance(ex)) {
                final var status = entry.getValue().status();
                if (status != null) {
                    response.setStatus(status);
                }
                return entry.getValue().mav();
            }
        }
        return switch (ex) {
            case RestClientException rce -> {
                response.setStatus(HttpStatus.BAD_GATEWAY.value());
                logger.warn(String.format("upstream exception in %s: %s", handler, ex.getMessage()));
                yield defaultMav;
            }
            default -> {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                logger.warn(String.format("unhandled exception in %s", handler), ex);
                yield defaultMav;
            }
        };
    }

    public record ModelViewStatus(ModelAndView mav, Integer status) {

    }

    public static class Builder {

        private ModelAndView errorAction = new ModelAndView("error");
        private final Map<Class<? extends Exception>, ModelViewStatus> exceptionToView = new LinkedHashMap<>();

        public Builder with(String defaultView) {
            this.errorAction = new ModelAndView(defaultView);
            return this;
        }

        public Builder with(ModelAndView defaultMav) {
            this.errorAction = defaultMav;
            return this;
        }

        public Builder with(Class<? extends Exception> clazz, String view, Integer status) {
            this.exceptionToView.put(clazz, new ModelViewStatus(new ModelAndView(view), status));
            return this;
        }

        public Builder with(Class<? extends Exception> clazz, String view) {
            this.exceptionToView.put(clazz, new ModelViewStatus(new ModelAndView(view), null));
            return this;
        }

        public Builder with(Class<? extends Exception> clazz, ModelAndView mav, Integer status) {
            this.exceptionToView.put(clazz, new ModelViewStatus(mav, status));
            return this;
        }

        public Builder with(Class<? extends Exception> clazz, ModelAndView mav) {
            this.exceptionToView.put(clazz, new ModelViewStatus(mav, null));
            return this;
        }

        public PagesExceptionResolver build() {
            return new PagesExceptionResolver(errorAction, exceptionToView);
        }

    }
}
