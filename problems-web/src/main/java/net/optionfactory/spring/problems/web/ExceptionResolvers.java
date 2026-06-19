package net.optionfactory.spring.problems.web;

import java.util.List;
import java.util.function.Consumer;
import org.springframework.web.servlet.HandlerExceptionResolver;
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

    public void configure() {
        if (pages != null) {
            container.addFirst(pages);
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
