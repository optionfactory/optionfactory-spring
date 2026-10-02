package net.optionfactory.spring.problems.web;

import java.util.List;
import java.util.function.Consumer;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.json.JsonMapper;

/// Installs this module's exception resolvers into spring mvc's chain, each in the place it needs.
///
/// Spring asks the resolvers in order, and the first returning a `ModelAndView` answers the
/// exception: where a resolver sits decides which exceptions it gets to see. Pick the resolvers the
/// application needs, then [#configure()] places them:
///
/// ```java
/// @Configuration
/// @EnableWebMvc
/// public class WebConfig implements WebMvcConfigurer {
///
///     @Override
///     public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
///         ExceptionResolvers.configurer(resolvers)
///                 .undeliverables()
///                 .rest(jsonMapper, rest -> rest.withMessageSource(messageSource))
///                 .binaries()
///                 .pages()
///                 .configure();
///     }
/// }
/// ```
///
/// Nothing is installed until [#configure()] is called; configuring the same resolver twice keeps
/// the last configuration.
public class ExceptionResolvers {

    private final List<HandlerExceptionResolver> container;
    private UndeliverableResponseExceptionResolver undeliverables;
    private BinaryResponseExceptionResolver binaries;
    private RestExceptionResolver rest;
    private PagesExceptionResolver pages;

    /// @param container spring's resolver chain, modified in place by [#configure()]
    public ExceptionResolvers(List<HandlerExceptionResolver> container) {
        this.container = container;
    }

    /// Answers the exceptions of `@ResponseBody` handlers with problems, see [RestExceptionResolver].
    ///
    /// @param mapper the mapper problems are serialized with
    /// @param c customizes the resolver's builder
    /// @return this configurer
    public ExceptionResolvers rest(JsonMapper mapper, Consumer<RestExceptionResolver.Builder> c) {
        final var builder = RestExceptionResolver.builder();
        c.accept(builder);
        this.rest = builder.build(mapper);
        return this;
    }

    /// Answers the exceptions of `@ResponseBody` handlers with problems, see [RestExceptionResolver],
    /// with the default configuration: built-in classifiers only, details omitted.
    ///
    /// @param mapper the mapper problems are serialized with
    /// @return this configurer
    public ExceptionResolvers rest(JsonMapper mapper) {
        this.rest = RestExceptionResolver.builder().build(mapper);
        return this;
    }

    /// Answers the exceptions of page handlers with the `error` view, see [PagesExceptionResolver].
    ///
    /// @return this configurer
    public ExceptionResolvers pages() {
        this.pages = PagesExceptionResolver.builder().build();
        return this;
    }

    /// Answers the exceptions of page handlers with views, see [PagesExceptionResolver].
    ///
    /// @param c customizes the resolver's builder
    /// @return this configurer
    public ExceptionResolvers pages(Consumer<PagesExceptionResolver.Builder> c) {
        final var builder = PagesExceptionResolver.builder();
        c.accept(builder);
        this.pages = builder.build();
        return this;
    }

    /// Declines, ahead of every other resolver, when the client has gone or the
    /// response is already committed, so the resolvers behind it are not asked to
    /// answer with a problem document or an error page the request can no longer
    /// carry. Add it when anything in the application streams: server-sent events,
    /// a `StreamingResponseBody` download, or any response written incrementally.
    ///
    /// @return this configurer
    public ExceptionResolvers undeliverables() {
        this.undeliverables = new UndeliverableResponseExceptionResolver();
        return this;
    }

    /// Answers the exceptions of download handlers with a bare status, see
    /// [BinaryResponseExceptionResolver].
    ///
    /// @return this configurer
    public ExceptionResolvers binaries() {
        this.binaries = new BinaryResponseExceptionResolver();
        return this;
    }

    /// Places the configured resolvers in the chain:
    ///
    /// - [UndeliverableResponseExceptionResolver] first, so that the resolvers behind it are never
    ///   asked to write a response that is gone;
    /// - then [RestExceptionResolver];
    /// - then [BinaryResponseExceptionResolver], so that it only sees the downloads of handlers
    ///   that are not `@ResponseBody`: a `@RestController` serving a file is answered by the rest
    ///   resolver, whenever both are configured;
    /// - [PagesExceptionResolver] right after spring's `ExceptionHandlerExceptionResolver`, so that
    ///   `@ExceptionHandler` methods still take precedence for pages, or ahead of every resolver
    ///   already in the chain when there is none.
    ///
    /// With `@EnableWebMvc` and every resolver configured, the chain becomes:
    ///
    /// | resolver | notes |
    /// | -------- | ----- |
    /// | `UndeliverableResponseExceptionResolver` | declines when the client is gone or the response is committed |
    /// | `RestExceptionResolver` | `@ResponseBody` handlers |
    /// | `BinaryResponseExceptionResolver` | download handlers |
    /// | `ExceptionHandlerExceptionResolver` | `@ExceptionHandler` methods, in practice only for pages |
    /// | `PagesExceptionResolver` | every other exception except `AccessDeniedException` |
    /// | `ResponseStatusExceptionResolver` | generally unused, the resolvers above answer first |
    /// | `DefaultHandlerExceptionResolver` | generally unused, the resolvers above answer first |
    ///
    /// An `AccessDeniedException` thrown by a page handler, and not handled by an
    /// `@ExceptionHandler`, is declined by every resolver, and so reaches spring security's
    /// `ExceptionTranslationFilter`. Call this method once: each call adds the resolvers again.
    public void configure() {
        int eherIndex = -1;
        for (int i = 0; i < container.size(); i++) {
            if (container.get(i) instanceof ExceptionHandlerExceptionResolver) {
                eherIndex = i;
                break;
            }
        }
        if (pages != null) {
            container.add(eherIndex + 1, pages);
        }
        if (binaries != null) {
            container.addFirst(binaries);
        }
        if (rest != null) {
            container.addFirst(rest);
        }
        if (undeliverables != null) {
            container.addFirst(undeliverables);
        }
    }

    /// @param container spring's resolver chain, as passed to
    ///        `WebMvcConfigurer.extendHandlerExceptionResolvers`
    /// @return a configurer installing nothing until told to
    public static ExceptionResolvers configurer(List<HandlerExceptionResolver> container) {
        return new ExceptionResolvers(container);
    }
}
