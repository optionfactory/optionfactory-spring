package net.optionfactory.spring.problems.web;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import tools.jackson.databind.json.JsonMapper;

public class ExceptionResolversTest {

    private static List<Class<?>> types(List<HandlerExceptionResolver> chain) {
        return chain.stream().<Class<?>>map(Object::getClass).toList();
    }

    @Test
    public void everyResolverIsPlacedWhereItBelongsInSpringsChain() {
        final List<HandlerExceptionResolver> chain = new ArrayList<>(List.of(new ExceptionHandlerExceptionResolver(), new ResponseStatusExceptionResolver(), new DefaultHandlerExceptionResolver()));
        ExceptionResolvers.configurer(chain).pages().binaries().rest(new JsonMapper()).undeliverables().configure();
        Assertions.assertEquals(List.of(
                UndeliverableResponseExceptionResolver.class,
                RestExceptionResolver.class,
                BinaryResponseExceptionResolver.class,
                ExceptionHandlerExceptionResolver.class,
                PagesExceptionResolver.class,
                ResponseStatusExceptionResolver.class,
                DefaultHandlerExceptionResolver.class
        ), types(chain), "the order must not depend on the order the resolvers were configured in");
    }

    @Test
    public void withoutAnExceptionHandlerResolverPagesGoAheadOfSpringsResolvers() {
        final List<HandlerExceptionResolver> chain = new ArrayList<>(List.of(new DefaultHandlerExceptionResolver()));
        ExceptionResolvers.configurer(chain).rest(new JsonMapper()).pages().configure();
        Assertions.assertEquals(List.of(
                RestExceptionResolver.class,
                PagesExceptionResolver.class,
                DefaultHandlerExceptionResolver.class
        ), types(chain), "pages must come right after the resolvers of this module, ahead of spring's");
    }

    @Test
    public void nothingIsInstalledUntilConfigured() {
        final List<HandlerExceptionResolver> chain = new ArrayList<>();
        ExceptionResolvers.configurer(chain).rest(new JsonMapper()).pages();
        Assertions.assertEquals(List.of(), chain, "the chain must be left alone until configure() is called");
    }
}
