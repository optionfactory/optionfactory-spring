package net.optionfactory.spring.problems.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/// Answers the exceptions of page handlers with an error view.
///
/// An exception is matched against the mappings registered with the [Builder], in registration
/// order, the first mapping whose class the exception is an instance of answering it with its view,
/// and with its status when it has one: a mapping without status leaves the response status as it
/// is, which is what a mapping to a redirect (`"redirect:/"`) wants. An unmapped exception is
/// answered with the default view, `error` unless configured otherwise, and:
///
/// - `502` for a `RestClientException`, logged at `WARN`;
/// - the status of a spring `ErrorResponse` (a `ResponseStatusException`, a missing parameter's
///   `400`, an unsupported method's `405`, the `404` of a `NoResourceFoundException` for an unknown
///   url);
/// - the status of an exception class annotated with `@ResponseStatus`;
/// - `500` for anything else, logged at `WARN` with its stack trace.
///
/// A `4xx` is logged at `DEBUG`, as a client error.
///
/// An `AccessDeniedException` is always declined, before any mapping is consulted, so that it
/// reaches spring security's `ExceptionTranslationFilter`, which starts the authentication of an
/// anonymous user and hands anyone else to its access denied handler.
///
/// The resolver answers any handler: it relies on its place in the chain, behind the rest and
/// binary resolvers, to see only page handlers, see [ExceptionResolvers#configure()]. The
/// `ModelAndView`s configured are templates: each resolution answers with a fresh copy of one, with
/// the same view, status and model entries, so a request can modify the one it gets without
/// affecting the others.
///
/// ```java
/// ExceptionResolvers.configurer(resolvers)
///         .pages(pages -> pages
///                 .with("errors/generic")
///                 .with(EntityNotFoundException.class, "errors/not-found", 404))
///         .configure();
/// ```
public class PagesExceptionResolver implements HandlerExceptionResolver {

    private final Logger logger = LoggerFactory.getLogger(PagesExceptionResolver.class);
    private final ModelAndView defaultMav;
    private final Map<Class<? extends Exception>, ModelViewStatus> exceptionToView;

    /// @param defaultMav the template of the answer to an exception no mapping matches, copied for
    ///        each resolution
    /// @param exceptionToView the mappings, consulted in the map's iteration order, their
    ///        `ModelAndView`s copied for each resolution
    public PagesExceptionResolver(ModelAndView defaultMav, Map<Class<? extends Exception>, ModelViewStatus> exceptionToView) {
        this.defaultMav = defaultMav;
        this.exceptionToView = exceptionToView;
    }

    /// @return a builder answering every exception with the `error` view
    public static Builder builder() {
        return new Builder();
    }

    /// @param request the current request
    /// @param response the current response, whose status is set
    /// @param handler the handler that threw, possibly `null`
    /// @param ex the exception
    /// @return the view to render, or `null` for an `AccessDeniedException`
    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (ex instanceof AccessDeniedException) {
            return null;
        }
        for (var entry : exceptionToView.entrySet()) {
            if (entry.getKey().isInstance(ex)) {
                final var status = entry.getValue().status();
                if (status != null) {
                    response.setStatus(status);
                }
                return copyOf(entry.getValue().mav());
            }
        }
        return switch (ex) {
            case RestClientException rce -> {
                response.setStatus(HttpStatus.BAD_GATEWAY.value());
                logger.warn(String.format("upstream exception in %s: %s", handler, ex.getMessage()));
                yield copyOf(defaultMav);
            }
            case ErrorResponse er -> {
                response.setStatus(er.getStatusCode().value());
                if (er.getStatusCode().is4xxClientError()) {
                    logger.debug(String.format("client error in %s: %s", handler, ex));
                } else {
                    logger.warn(String.format("error response in %s: %s", handler, ex));
                }
                yield copyOf(defaultMav);
            }
            default -> {
                final var status = ExceptionClassifier.annotatedStatusOr(ex, HttpStatus.INTERNAL_SERVER_ERROR);
                response.setStatus(status.value());
                if (status.is4xxClientError()) {
                    logger.debug(String.format("client error in %s: %s", handler, ex));
                } else {
                    logger.warn(String.format("unhandled exception in %s", handler), ex);
                }
                yield copyOf(defaultMav);
            }
        };
    }

    private static ModelAndView copyOf(ModelAndView template) {
        final var mav = new ModelAndView();
        if (template.isReference()) {
            mav.setViewName(template.getViewName());
        } else {
            mav.setView(template.getView());
        }
        mav.setStatus(template.getStatus());
        mav.addAllObjects(template.getModel());
        return mav;
    }

    /// How a mapped exception is answered.
    ///
    /// @param mav the view to render
    /// @param status the status to set, or `null` to leave the response's as it is
    public record ModelViewStatus(ModelAndView mav, Integer status) {

    }

    /// Configures a [PagesExceptionResolver]. Registering a mapping for a class already mapped
    /// replaces its view and status, but keeps its place in the order. Mappings registered after
    /// [#build()] do not affect the resolvers already built.
    public static class Builder {

        private ModelAndView errorAction = new ModelAndView("error");
        private final Map<Class<? extends Exception>, ModelViewStatus> exceptionToView = new LinkedHashMap<>();

        /// @param defaultView the name of the view answering an exception no mapping matches
        /// @return this builder
        public Builder with(String defaultView) {
            this.errorAction = new ModelAndView(defaultView);
            return this;
        }

        /// @param defaultMav the answer to an exception no mapping matches
        /// @return this builder
        public Builder with(ModelAndView defaultMav) {
            this.errorAction = defaultMav;
            return this;
        }

        /// @param clazz the exceptions to answer, subclasses included
        /// @param view the name of the view answering them
        /// @param status the status to set, or `null` to leave the response's as it is
        /// @return this builder
        public Builder with(Class<? extends Exception> clazz, String view, Integer status) {
            this.exceptionToView.put(clazz, new ModelViewStatus(new ModelAndView(view), status));
            return this;
        }

        /// Maps exceptions to a view, leaving the response status as it is: meant for redirects,
        /// which set their own status, e.g. `with(ServletRequestBindingException.class,
        /// "redirect:/")`. Any other view is rendered with the current status, usually `200`.
        ///
        /// @param clazz the exceptions to answer, subclasses included
        /// @param view the name of the view answering them
        /// @return this builder
        public Builder with(Class<? extends Exception> clazz, String view) {
            this.exceptionToView.put(clazz, new ModelViewStatus(new ModelAndView(view), null));
            return this;
        }

        /// @param clazz the exceptions to answer, subclasses included
        /// @param mav the answer to them
        /// @param status the status to set, or `null` to leave the response's as it is
        /// @return this builder
        public Builder with(Class<? extends Exception> clazz, ModelAndView mav, Integer status) {
            this.exceptionToView.put(clazz, new ModelViewStatus(mav, status));
            return this;
        }

        /// Maps exceptions to a view, leaving the response status as it is: meant for redirects,
        /// which set their own status, e.g. `with(ServletRequestBindingException.class,
        /// "redirect:/")`. Any other view is rendered with the current status, usually `200`.
        ///
        /// @param clazz the exceptions to answer, subclasses included
        /// @param mav the answer to them
        /// @return this builder
        public Builder with(Class<? extends Exception> clazz, ModelAndView mav) {
            this.exceptionToView.put(clazz, new ModelViewStatus(mav, null));
            return this;
        }

        /// @return the configured resolver
        public PagesExceptionResolver build() {
            return new PagesExceptionResolver(errorAction, new LinkedHashMap<>(exceptionToView));
        }

    }
}
