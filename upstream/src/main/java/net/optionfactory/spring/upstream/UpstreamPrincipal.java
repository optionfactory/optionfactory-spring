package net.optionfactory.spring.upstream;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.MethodParameter;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.service.invoker.HttpServiceArgumentResolver;

@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = ElementType.PARAMETER)
public @interface UpstreamPrincipal {

    public static class ArgumentResolver implements HttpServiceArgumentResolver {

        @Override
        public boolean resolve(Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
            return parameter.getParameterType() == UpstreamPrincipal.class;
        }

    }
}
