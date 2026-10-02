package net.optionfactory.spring.context.fieldaccess;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.spring.context.fieldaccess.EnableCustomWebMvc.CustomizableDelegatingWebMvcConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.validation.Validator;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/// Enables spring mvc like [EnableWebMvc], with two customisations:
///
/// - data binding (request parameters bound to `@ModelAttribute` and command objects) accesses the
///   objects' fields directly instead of going through getters and setters, so form objects can be
///   plain classes without accessors. It applies to data binding only, not
///   to message converters such as the json ones;
/// - the `LocaleResolver` is the bean named, or qualified, `customLocaleResolver` when there is one,
///   and spring's default `AcceptHeaderLocaleResolver` otherwise. A bean named `localeResolver` is not
///   the way to customise it: that name is already taken by the locale resolver this configuration
///   declares itself.
///
/// Use it in place of `@EnableWebMvc`, never together with it; `WebMvcConfigurer`s are applied as
/// usual.
///
/// Direct field access exists for DTO classes with public fields and no setters: spring's default
/// binder only writes bean properties, so it ignores those fields without reporting a binding
/// error, and the request parameters are silently lost. It has two costs:
///
/// - a record cannot be bound from request parameters. Spring constructs it from the parameters,
///   then also tries to write them into the record's final fields, which fails: the request is
///   answered `400`. A class with a nested record property fails with a `500`. Plain
///   `@EnableWebMvc` binds both correctly;
/// - every field can be written by a request parameter named after it, private fields without
///   setters included, so a bound class must not carry fields a client must not set.
///
/// Direct field access can be dropped once no type bound from request parameters relies on fields
/// without setters, i.e. when every such type is a record or a JavaBean; types read only from json
/// bodies do not count. See `docs/decisions/0011-field-binding.md`.
///
/// ```java
/// @Configuration
/// @EnableCustomWebMvc
/// public class WebConfig implements WebMvcConfigurer {
///
///     @Bean
///     public LocaleResolver customLocaleResolver() {
///         return new CookieLocaleResolver("lang");
///     }
/// }
/// ```
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(CustomizableDelegatingWebMvcConfiguration.class)
public @interface EnableCustomWebMvc {

    /// The configuration imported by [EnableCustomWebMvc]: spring's `DelegatingWebMvcConfiguration`,
    /// which applies the context's `WebMvcConfigurer`s, with direct field access and the custom
    /// locale resolver.
    @Configuration
    public static class CustomizableDelegatingWebMvcConfiguration extends DelegatingWebMvcConfiguration {

        private final Logger logger = LoggerFactory.getLogger(CustomizableDelegatingWebMvcConfiguration.class);
        private final ObjectProvider<LocaleResolver> customLocaleResolver;

        /// @param customLocaleResolver the beans named or qualified `customLocaleResolver`
        public CustomizableDelegatingWebMvcConfiguration(@Qualifier("customLocaleResolver") ObjectProvider<LocaleResolver> customLocaleResolver) {
            this.customLocaleResolver = customLocaleResolver;
        }

        @Override
        protected ConfigurableWebBindingInitializer getConfigurableWebBindingInitializer(FormattingConversionService mvcConversionService, Validator mvcValidator) {
            final ConfigurableWebBindingInitializer initializer = super.getConfigurableWebBindingInitializer(mvcConversionService, mvcValidator);
            initializer.setDirectFieldAccess(true);
            return initializer;
        }

        /// Logs, at INFO or WARN, which locale resolver is in use.
        ///
        /// @return the `customLocaleResolver` bean, or spring's default `AcceptHeaderLocaleResolver`
        /// when there is none
        /// @throws IllegalStateException when more than one bean is named or qualified
        /// `customLocaleResolver`, failing the context refresh
        @Override
        public LocaleResolver localeResolver() {
            final var resolvers = customLocaleResolver.stream().toList();
            if (resolvers.isEmpty()) {
                logger.warn("LocaleResolver: bean 'customLocaleResolver' not found: using the default AcceptHeaderLocaleResolver. A bean named 'localeResolver' is NOT picked up: the conventional name collides with WebMvcConfigurationSupport's own bean.");
                return super.localeResolver();
            }
            if (resolvers.size() == 1) {
                logger.info("LocaleResolver: bean 'customLocaleResolver' found: configured");
                return resolvers.get(0);
            }
            throw new IllegalStateException(String.format("multiple conflicting locale resolvers found: %s", customLocaleResolver.stream().map(r -> r.getClass().getName()).toList()));
        }

    }
}
