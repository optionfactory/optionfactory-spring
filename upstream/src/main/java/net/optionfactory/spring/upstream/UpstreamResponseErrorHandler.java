package net.optionfactory.spring.upstream;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;

public interface UpstreamResponseErrorHandler {

    default void preprocess(Class<?> k, Map<Method, EndpointDescriptor> endpoints) {
    }

    boolean hasError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException;

    void handleError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException;

}
