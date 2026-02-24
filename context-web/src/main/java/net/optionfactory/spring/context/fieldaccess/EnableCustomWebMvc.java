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

/**
 * To be used in place of {@link EnableWebMvc @EnableWebMvc}, enforcing access
 * to bean fields instead of referencing getters and setters.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(CustomizableDelegatingWebMvcConfiguration.class)
public @interface EnableCustomWebMvc {

    @Configuration
    public static class CustomizableDelegatingWebMvcConfiguration extends DelegatingWebMvcConfiguration {

        private final Logger logger = LoggerFactory.getLogger(CustomizableDelegatingWebMvcConfiguration.class);
        private final ObjectProvider<LocaleResolver> customLocaleResolver;

        public CustomizableDelegatingWebMvcConfiguration(@Qualifier("customLocaleResolver") ObjectProvider<LocaleResolver> customLocaleResolver) {
            this.customLocaleResolver = customLocaleResolver;
        }

        @Override
        protected ConfigurableWebBindingInitializer getConfigurableWebBindingInitializer(FormattingConversionService mvcConversionService, Validator mvcValidator) {
            final ConfigurableWebBindingInitializer initializer = super.getConfigurableWebBindingInitializer(mvcConversionService, mvcValidator);
            initializer.setDirectFieldAccess(true);
            return initializer;
        }

        @Override
        public LocaleResolver localeResolver() {
            final var resolvers = customLocaleResolver.stream().toList();
            if(resolvers.isEmpty()){
                logger.info("LocaleResolver: bean 'customLocaleResolver' not found: using the default localeResolver");
                return super.localeResolver();
            }
            if(resolvers.size() == 1){
                logger.info("LocaleResolver: bean 'customLocaleResolver' found: configured");
                return resolvers.get(0);
            }
            throw new IllegalStateException(String.format("multiple conflicting locale resolvers found: %s", resolvers));
        }

    }
}
