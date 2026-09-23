package net.optionfactory.spring.problems.web;

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
    private final List<ExceptionClassifier> classifiers;
    private final List<FailureTransformer> transformers;
    private final MessageSource messageSource;

    /**
     * Controls whether problem {@code details} (e.g. raw exception messages) are
     * serialized in error responses.
     * <p>
     * {@link #OMIT} is the default and <strong>must be used in production</strong>:
     * it strips any internal detail (exception messages, SQL state, class names,
     * file paths) so that only the localized {@code reason} is exposed to clients.
     * <p>
     * {@link #INCLUDE} is intended for <strong>development and debugging only</strong>:
     * it preserves the original {@code details} to help diagnose failures, but those
     * details may carry sensitive internal information and must never be exposed in a
     * production deployment.
     */
    public enum Details {
        INCLUDE, OMIT

    }

    public static Builder builder() {
        return new Builder();
    }

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
            return List.copyOf(modules);
        }

        private Details options = Details.OMIT;
        private final List<ExceptionClassifier> classifiers = new ArrayList<>();
        private final List<FailureTransformer> transformers = new ArrayList<>();
        private MessageSource messageSource;

        public Builder withMessageSource(MessageSource messageSource) {
            this.messageSource = messageSource;
            return this;
        }

        /// @deprecated upstream support is registered by default whenever `upstream` is on the
        ///             classpath; this only still fails when it is not
        @Deprecated
        public Builder withUpstreamTransformer() {
            if (!UPSTREAM_PRESENT) {
                throw new IllegalStateException("upstream is not on the classpath");
            }
            return this;
        }

        /// @deprecated upstream support is registered by default whenever `upstream` is on the classpath
        @Deprecated
        public Builder withUpstreamTransformerIfPresent() {
            return this;
        }

        public Builder withDetails(Details options) {
            this.options = options;
            return this;
        }

        public Builder withDetails(boolean include) {
            this.options = include ? Details.INCLUDE : Details.OMIT;
            return this;
        }

        public Builder withDetails() {
            this.options = Details.INCLUDE;
            return this;
        }

        public Builder withoutDetails() {
            this.options = Details.OMIT;
            return this;
        }

        public Builder withTransformer(FailureTransformer t) {
            this.transformers.add(t);
            return this;
        }

        public Builder withClassifier(ExceptionClassifier c) {
            this.classifiers.add(c);
            return this;
        }

        public Builder withModule(ProblemsModule module) {
            module.classifiers().forEach(this::withClassifier);
            module.transformers().forEach(this::withTransformer);
            return this;
        }

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

    public RestExceptionResolver(JsonMapper mapper, MessageSource messageSource, List<ExceptionClassifier> classifiers, List<FailureTransformer> transformers) {
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
            if (request.getAttribute("javax.servlet.error.exception") != null) {
                logger.warn(String.format("got an internal error from spring at %s", requestUri), ex);
            }
            final HttpStatus currentStatus = HttpStatus.valueOf(response.getStatus());
            logger.warn(String.format("got an unexpected error while processing request at %s", requestUri), ex);
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

    public static record HttpStatusAndProblems(HttpStatusCode status, List<Problem> problems) {

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
