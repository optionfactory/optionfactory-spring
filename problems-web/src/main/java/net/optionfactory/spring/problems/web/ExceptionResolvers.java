package net.optionfactory.spring.problems.web;

import java.util.List;
import java.util.function.Consumer;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.json.JsonMapper;

public class ExceptionResolvers {

    private final List<HandlerExceptionResolver> container;
    private BinaryResponseExceptionResolver binaries;
    private RestExceptionResolver rest;
    private PagesExceptionResolver pages;

    public ExceptionResolvers(List<HandlerExceptionResolver> container) {
        this.container = container;
    }

    public ExceptionResolvers rest(JsonMapper mapper, Consumer<RestExceptionResolver.Builder> c) {
        final var builder = RestExceptionResolver.builder();
        c.accept(builder);
        this.rest = builder.build(mapper);
        return this;
    }

    public ExceptionResolvers rest(JsonMapper mapper) {
        this.rest = RestExceptionResolver.builder().withUpstreamTransformerIfPresent().build(mapper);
        return this;
    }

    public ExceptionResolvers pages() {
        this.pages = PagesExceptionResolver.builder().build();
        return this;
    }

    public ExceptionResolvers pages(Consumer<PagesExceptionResolver.Builder> c) {
        final var builder = PagesExceptionResolver.builder();
        c.accept(builder);
        this.pages = builder.build();
        return this;
    }

    public ExceptionResolvers binaries() {
        this.binaries = new BinaryResponseExceptionResolver();
        return this;
    }

    /// Places the configured exception resolvers in the chain, in this order:
    ///   - RestExceptionResolver: on top 
    ///   - BinaryResponseExceptionResolver: on top, after the
    ///     RestExceptionResolver if it's configured'
    ///   - PagesExceptionResolver: after ExceptionHandlerExceptionResolver
    ///     if it's confifgured, on top after BinaryResponseExceptionResolver
    ///     otherwhise'(if it's configured'))
    ///
    ///  By default, if they are all configured the chain looks like this:
    ///
    /// | handler | notes  |
    /// | ------- | ------ |
    /// | `RestExceptionResolver` |  |
    /// | `BinaryResponseExceptionResolver` | |
    /// | `ExceptionHandlerExceptionResolver` | handles `@ExceptionHandler`, but only for pages |
    /// | `PagesExceptionResolver` |  |
    /// | `ResponseStatusExceptionResolver` | generally useless, handled by the other resolvers if configured |
    /// | `AccessDeniedExceptionResolver` | rethrows `AccessDeniedException`s so they can be handled by the `ExceptionTranslationFilter` |
    /// | `DefaultHandlerExceptionResolver` | generally useless, handled by other resolvers if configured |
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
    }

    public static ExceptionResolvers configurer(List<HandlerExceptionResolver> container) {
        return new ExceptionResolvers(container);
    }
}
