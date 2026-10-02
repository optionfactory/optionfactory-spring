package net.optionfactory.spring.problems.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.l10n.AggregateMessageSource;
import net.optionfactory.spring.problems.web.datajpa.DataJpaProblemsModule;
import net.optionfactory.spring.problems.web.l10n.FallbackMessageSource;
import net.optionfactory.spring.problems.web.upstream.UpstreamProblemsModule;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import org.springframework.web.servlet.view.json.JacksonJsonView;
import tools.jackson.databind.json.JsonMapper;

/// Answers the exceptions thrown by `@ResponseBody` handlers, `@RestController`s included, with a
/// json list of [Problem]s and an http status, served as `application/failures+json`:
///
/// ```json
/// [
///   {"type": "FIELD_ERROR", "context": "name", "reason": "must not be null", "details": null},
///   {"type": "OBJECT_ERROR", "context": null, "reason": "a global error", "details": null}
/// ]
/// ```
///
/// Each exception is offered to the [ExceptionClassifier]s: those registered with the [Builder]
/// first, in registration order, then the built-in ones, which answer spring mvc's own exceptions
/// ([SpringWebProblemsModule]), bean validation's ([BeanValidationProblemsModule]), the
/// application's [Failure][net.optionfactory.spring.problems.Failure]s ([FailureProblemsModule]),
/// spring security's `AccessDeniedException` ([SpringSecurityProblemsModule]), spring's remaining
/// `ErrorResponse`s ([ErrorResponseProblemsModule]) and, when those libraries are on the classpath,
/// `upstream`'s and `data-jpa`'s. The resolver logs a classified exception at `DEBUG`, as a client
/// error; modules log the server-side failures they classify themselves.
///
/// An exception no classifier claims is unexpected, and answered with a single `SERVER_ERROR`
/// problem: with the status spring's `DefaultHandlerExceptionResolver` picks for it, logged at
/// `WARN`, when spring knows the exception, as an internal error of spring's when spring marks it so
/// (by setting `RequestDispatcher.ERROR_EXCEPTION`, as for an `HttpMessageNotWritableException`),
/// and with a `500`, logged at `ERROR`, otherwise. In both
/// cases an exception class annotated with `@ResponseStatus` is answered with that status instead,
/// but still logged as unexpected.
///
/// The [FailureTransformer]s then run, built-in ones first, and finally, unless details are
/// included, the problems' `details` are cleared: they carry exception messages and other internal
/// state that must not reach clients in production (see [Details]).
///
/// The resolver declines handlers that are not `HandlerMethod`s or not `@ResponseBody`, leaving
/// them to the resolvers behind it. It is usually installed with [ExceptionResolvers]:
///
/// ```java
/// @Override
/// public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
///     ExceptionResolvers.configurer(resolvers)
///             .rest(jsonMapper, rest -> rest.withMessageSource(messageSource).withClassifier(new MyLibraryClassifier()))
///             .configure();
/// }
/// ```
public class RestExceptionResolver extends DefaultHandlerExceptionResolver {

    private final Map<HandlerMethod, Boolean> methodToIsRest = new ConcurrentHashMap<>();
    private final JsonMapper mapper;
    private final List<ExceptionClassifier> classifiers;
    private final List<FailureTransformer> transformers;
    private final MessageSource messageSource;

    /// Controls whether problem `details` (e.g. raw exception messages) are serialized in error
    /// responses.
    ///
    /// [#OMIT] is the default and **must be used in production**: it strips any internal detail
    /// (exception messages, SQL state, class names, file paths) so that only the localized `reason`
    /// is exposed to clients.
    ///
    /// [#INCLUDE] is intended for **development and debugging only**: it preserves the original
    /// `details` to help diagnose failures, but those details may carry sensitive internal
    /// information and must never be exposed in a production deployment.
    public enum Details {
        /// Keep the problems' details, for development and debugging only.
        INCLUDE,
        /// Clear the problems' details after every transformer has run, the default.
        OMIT

    }

    /// @return a builder for a resolver with the built-in classifiers, details omitted
    public static Builder builder() {
        return new Builder();
    }

    /// Configures a [RestExceptionResolver]. The built-in modules are always registered: their
    /// classifiers are consulted after those registered here, so that these can refine a built-in
    /// case, and their transformers run before those registered here, so that these see the
    /// built-in transformations.
    public static class Builder {

        private static final ClassLoader LOADER = RestExceptionResolver.class.getClassLoader();
        private static final boolean DATA_JPA_PRESENT = ClassUtils.isPresent("net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest", LOADER);
        private static final boolean UPSTREAM_PRESENT = ClassUtils.isPresent("net.optionfactory.spring.upstream.errors.RestClientUpstreamException", LOADER);
        private static final boolean BEAN_VALIDATION_PRESENT = ClassUtils.isPresent("jakarta.validation.ConstraintViolationException", LOADER);
        private static final boolean SPRING_SECURITY_PRESENT = ClassUtils.isPresent("org.springframework.security.access.AccessDeniedException", LOADER);
        private static final List<ProblemsModule> BUILT_INS = builtIns();

        /// Our modules, each registered when the library it integrates is present: a module's class is
        /// only loaded when it is instantiated, so one whose library is missing is never loaded.
        private static List<ProblemsModule> builtIns() {
            final var modules = new ArrayList<ProblemsModule>();
            if (DATA_JPA_PRESENT) {
                modules.add(new DataJpaProblemsModule());
            }
            if (UPSTREAM_PRESENT) {
                modules.add(new UpstreamProblemsModule());
            }
            modules.add(new SpringWebProblemsModule());
            if (BEAN_VALIDATION_PRESENT) {
                modules.add(new BeanValidationProblemsModule());
            }
            modules.add(new FailureProblemsModule());
            if (SPRING_SECURITY_PRESENT) {
                modules.add(new SpringSecurityProblemsModule());
            }
            modules.add(new ErrorResponseProblemsModule());
            return List.copyOf(modules);
        }

        private Details options = Details.OMIT;
        private final List<ExceptionClassifier> classifiers = new ArrayList<>();
        private final List<FailureTransformer> transformers = new ArrayList<>();
        private MessageSource messageSource;

        /// Sets the message source reasons are localized with.
        ///
        /// Without one, messages are resolved from the `ValidationMessages` and hibernate
        /// validator's bundles, then from every `ContributorValidationMessages` bundle on the
        /// classpath, where this module ships its own messages (`error.missing_parameter`,
        /// `error.invalid_format`, ...). With one, it is consulted first and those bundles only for
        /// the codes it does not know: a message source configured to use the code as the default
        /// message knows every code, and so hides them.
        ///
        /// @param messageSource the application's message source
        /// @return this builder
        public Builder withMessageSource(MessageSource messageSource) {
            this.messageSource = messageSource;
            return this;
        }

        /// @return this builder
        /// @throws IllegalStateException when `upstream` is not on the classpath
        /// @deprecated upstream support is registered by default whenever `upstream` is on the
        ///             classpath; this only still fails when it is not
        @Deprecated
        public Builder withUpstreamTransformer() {
            if (!UPSTREAM_PRESENT) {
                throw new IllegalStateException("upstream is not on the classpath");
            }
            return this;
        }

        /// @return this builder
        /// @deprecated upstream support is registered by default whenever `upstream` is on the classpath
        @Deprecated
        public Builder withUpstreamTransformerIfPresent() {
            return this;
        }

        /// @param options whether the problems' details reach the response
        /// @return this builder
        public Builder withDetails(Details options) {
            this.options = options;
            return this;
        }

        /// @param include true to include the problems' details, for development only
        /// @return this builder
        public Builder withDetails(boolean include) {
            this.options = include ? Details.INCLUDE : Details.OMIT;
            return this;
        }

        /// Includes the problems' details in responses, for development only.
        ///
        /// @return this builder
        public Builder withDetails() {
            this.options = Details.INCLUDE;
            return this;
        }

        /// Omits the problems' details from responses, the default.
        ///
        /// @return this builder
        public Builder withoutDetails() {
            this.options = Details.OMIT;
            return this;
        }

        /// Registers a transformer, run after the built-in ones and before details are omitted.
        ///
        /// @param t the transformer
        /// @return this builder
        public Builder withTransformer(FailureTransformer t) {
            this.transformers.add(t);
            return this;
        }

        /// Registers a classifier, consulted before the built-in ones in registration order.
        ///
        /// @param c the classifier
        /// @return this builder
        public Builder withClassifier(ExceptionClassifier c) {
            this.classifiers.add(c);
            return this;
        }

        /// Registers every classifier and transformer of a module, in the module's order.
        ///
        /// @param module the module
        /// @return this builder
        public Builder withModule(ProblemsModule module) {
            module.classifiers().forEach(this::withClassifier);
            module.transformers().forEach(this::withTransformer);
            return this;
        }

        /// @param mapper the mapper problems are serialized with
        /// @return the configured resolver
        public RestExceptionResolver build(JsonMapper mapper) {
            final var cs = new ArrayList<ExceptionClassifier>(classifiers);
            final var fts = new ArrayList<FailureTransformer>();
            for (final var module : BUILT_INS) {
                cs.addAll(module.classifiers());
                fts.addAll(module.transformers());
            }
            fts.addAll(transformers);
            if (options == Details.OMIT) {
                fts.add(new OmitDetails());
            }
            final var defaultSource = new ResourceBundleMessageSource();
            defaultSource.setBasenames("ValidationMessages", "org.hibernate.validator.ValidationMessages");
            defaultSource.setDefaultEncoding("UTF-8");
            defaultSource.setParentMessageSource(new AggregateMessageSource("ContributorValidationMessages"));
            final MessageSource ms = messageSource == null ? defaultSource : new FallbackMessageSource(messageSource, defaultSource);
            return new RestExceptionResolver(mapper, ms, List.copyOf(cs), fts);
        }

    }

    /// Builds a resolver from exactly what is given: unlike [#builder()], it registers no built-in
    /// classifier, and omits details only if an [OmitDetails] is among the transformers.
    ///
    /// @param mapper the mapper problems are serialized with
    /// @param messageSource the message source classifiers localize with
    /// @param classifiers the classifiers, in the order they are consulted
    /// @param transformers the transformers, in the order they run
    public RestExceptionResolver(JsonMapper mapper, MessageSource messageSource, List<ExceptionClassifier> classifiers, List<FailureTransformer> transformers) {
        setWarnLogCategory(null);
        this.mapper = mapper;
        this.classifiers = classifiers;
        this.transformers = transformers;
        this.messageSource = messageSource;
    }

    protected HttpStatusAndProblems toStatusAndErrors(HttpServletRequest request, HttpServletResponse response, HandlerMethod hm, Exception ex) {
        final String requestUri = request.getRequestURI();
        final var classified = classified(new ExceptionClassifier.Context(request, response, hm, messageSource, LocaleContextHolder.getLocale()), ex);
        if (classified != null) {
            logger.debug(String.format("Classified failure at %s: %s", requestUri, classified.problems()));
            return classified;
        }
        if (null != super.doResolveException(request, new SendErrorToSetStatusHttpServletResponse(response), hm, ex)) {
            if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) != null) {
                logger.warn(String.format("got an internal error from spring at %s", requestUri), ex);
            } else {
                logger.warn(String.format("got an unexpected error while processing request at %s", requestUri), ex);
            }
            final HttpStatus currentStatus = HttpStatus.valueOf(response.getStatus());
            return new HttpStatusAndProblems(ExceptionClassifier.annotatedStatusOr(ex, currentStatus), List.of(Problem.of(Problem.TYPE_SERVER_ERROR, null, null, ex.getMessage())));
        }
        logger.error(String.format("got an unexpected error while processing request at %s", requestUri), ex);
        return new HttpStatusAndProblems(ExceptionClassifier.annotatedStatusOr(ex, HttpStatus.INTERNAL_SERVER_ERROR), List.of(Problem.of(Problem.TYPE_SERVER_ERROR, null, null, ex.getMessage())));
    }

    private @Nullable HttpStatusAndProblems classified(ExceptionClassifier.Context context, Exception ex) {
        for (final var c : classifiers) {
            final HttpStatusAndProblems saps;
            try {
                saps = c.classify(context, ex);
            } catch (RuntimeException failure) {
                if (failure != ex) {
                    failure.addSuppressed(ex);
                }
                logger.error(String.format("classifier %s failed on %s at %s, skipped", c, ex.getClass().getName(), context.request().getRequestURI()), failure);
                continue;
            }
            if (saps != null) {
                return saps;
            }
        }
        return null;
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

    /// What an exception is answered with.
    ///
    /// @param status the response status
    /// @param problems the problems serialized as the response body
    public static record HttpStatusAndProblems(HttpStatusCode status, List<Problem> problems) {

    }

    /// Turns `sendError` into `setStatus`, so that spring's `DefaultHandlerExceptionResolver` can
    /// pick a status without committing the response with the container's error page: the
    /// resolver still has to write the problems.
    public static class SendErrorToSetStatusHttpServletResponse extends HttpServletResponseWrapper {

        private final HttpServletResponse inner;

        /// @param inner the response to set the status of
        public SendErrorToSetStatusHttpServletResponse(HttpServletResponse inner) {
            super(inner);
            this.inner = inner;
        }

        /// @param sc the status to set
        /// @param msg ignored
        @Override
        public void sendError(int sc, String msg) throws IOException {
            inner.setStatus(sc);
        }

        /// @param sc the status to set
        @Override
        public void sendError(int sc) throws IOException {
            inner.setStatus(sc);
        }
    }
}
