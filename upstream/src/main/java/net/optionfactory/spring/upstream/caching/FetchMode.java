package net.optionfactory.spring.upstream.caching;

import org.springframework.core.MethodParameter;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.service.invoker.HttpServiceArgumentResolver;

public enum FetchMode {
    ANY, FRESH;

    public static class ArgumentResolver implements HttpServiceArgumentResolver {

        @Override
        public boolean resolve(Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
            return parameter.getParameterType() == FetchMode.class;
        }

    }

}
