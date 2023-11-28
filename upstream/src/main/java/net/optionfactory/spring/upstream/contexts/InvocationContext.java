package net.optionfactory.spring.upstream.contexts;

import java.lang.reflect.Method;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor.HttpMessageConverters;

public record InvocationContext(
        HttpMessageConverters converters,
        String upstream,
        String endpoint,
        Method method,
        Object[] arguments,
        String boot,
        Object principal) {

}
