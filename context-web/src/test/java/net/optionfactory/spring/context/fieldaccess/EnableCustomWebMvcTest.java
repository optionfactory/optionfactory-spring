package net.optionfactory.spring.context.fieldaccess;

import java.util.Locale;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

public class EnableCustomWebMvcTest {

    @Configuration
    @EnableCustomWebMvc
    public static class BareConfig {

    }

    @Configuration
    @EnableCustomWebMvc
    public static class QualifiedResolverConfig {

        @Bean
        public LocaleResolver customLocaleResolver() {
            return new FixedLocaleResolver(Locale.ITALIAN);
        }

    }

    @Configuration
    @EnableCustomWebMvc
    public static class TwoQualifiedResolversConfig {

        @Bean
        public LocaleResolver customLocaleResolver() {
            return new FixedLocaleResolver(Locale.ITALIAN);
        }

        @Bean
        @org.springframework.beans.factory.annotation.Qualifier("customLocaleResolver")
        public LocaleResolver anotherCustomLocaleResolver() {
            return new FixedLocaleResolver(Locale.FRENCH);
        }

    }

    private static AnnotationConfigWebApplicationContext refreshed(Class<?> config) {
        final var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(config);
        context.refresh();
        return context;
    }

    @Test
    public void withoutACustomResolverTheDefaultApplies() {
        try (final var context = refreshed(BareConfig.class)) {
            Assertions.assertTrue(context.getBean("localeResolver", LocaleResolver.class) instanceof AcceptHeaderLocaleResolver,
                    "no customLocaleResolver bean: the default AcceptHeaderLocaleResolver applies");
        }
    }

    @Test
    public void theQualifiedCustomResolverIsUsed() {
        try (final var context = refreshed(QualifiedResolverConfig.class)) {
            Assertions.assertTrue(context.getBean("localeResolver", LocaleResolver.class) instanceof FixedLocaleResolver,
                    "a bean named customLocaleResolver must become the effective locale resolver");
        }
    }

    @Test
    public void twoCustomResolversAreRejected() {
        final var thrown = Assertions.assertThrows(Exception.class, () -> refreshed(TwoQualifiedResolversConfig.class));
        Assertions.assertTrue(thrown.getMessage().contains("multiple conflicting locale resolvers"),
                "the failure must say what conflicted, got: " + thrown.getMessage());
    }
}
