package net.optionfactory.spring.upstream;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;

public interface UpstreamHttpInterceptor {

    default void preprocess(Class<?> k, Map<Method, EndpointDescriptor> endpoints) {
    }

    ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException;

}
