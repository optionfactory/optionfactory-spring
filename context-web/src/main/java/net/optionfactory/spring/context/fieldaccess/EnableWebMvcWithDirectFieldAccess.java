package net.optionfactory.spring.context.fieldaccess;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.spring.context.fieldaccess.EnableWebMvcWithDirectFieldAccess.DirectFieldAccessConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.validation.Validator;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * To be used in place of {@link EnableWebMvc @EnableWebMvc}, enforcing access
 * to bean fields instead of referencing getters and setters.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(DirectFieldAccessConfig.class)
public @interface EnableWebMvcWithDirectFieldAccess {

    @Configuration
    static class DirectFieldAccessConfig extends DelegatingWebMvcConfiguration {

        @Override
        protected ConfigurableWebBindingInitializer getConfigurableWebBindingInitializer(FormattingConversionService mvcConversionService, Validator mvcValidator) {
            final ConfigurableWebBindingInitializer initializer = super.getConfigurableWebBindingInitializer(mvcConversionService, mvcValidator);
            initializer.setDirectFieldAccess(true);
            return initializer;
        }
    }
}
