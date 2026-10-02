package net.optionfactory.spring.context.fieldaccess;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

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
        final var thrown = Assertions.assertThrows(Exception.class, () -> refreshed(TwoQualifiedResolversConfig.class), "two custom locale resolvers fail the context refresh");
        Assertions.assertTrue(thrown.getMessage().contains("multiple conflicting locale resolvers"),
                "the failure must say what conflicted, got: " + thrown.getMessage());
    }

    public static class FieldsOnlyForm {

        private String name;
        private int age;

    }

    @Test
    public void requestParametersAreBoundToFieldsWithoutSetters() {
        try (final var context = refreshed(BareConfig.class)) {
            final var initializer = (ConfigurableWebBindingInitializer) context.getBean(RequestMappingHandlerAdapter.class).getWebBindingInitializer();
            final var form = new FieldsOnlyForm();
            final var binder = new WebDataBinder(form, "form");
            initializer.initBinder(binder);

            binder.bind(new MutablePropertyValues(Map.of("name", "alice", "age", "42")));

            Assertions.assertEquals("alice", form.name, "a private field without a setter is bound directly");
            Assertions.assertEquals(42, form.age, "the bound value is converted to the field's type");
        }
    }

    @Test
    public void theLocaleResolverIsTheOneTheDispatcherUses() {
        try (final var context = refreshed(QualifiedResolverConfig.class)) {
            final var resolver = context.getBean(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);
            Assertions.assertEquals(Locale.ITALIAN, resolver.resolveLocale(new MockHttpServletRequest()), "the dispatcher's localeResolver bean resolves with the custom resolver");
        }
    }
}
