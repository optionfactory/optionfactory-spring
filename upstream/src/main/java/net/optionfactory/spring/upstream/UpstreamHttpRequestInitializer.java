package net.optionfactory.spring.upstream;

import java.lang.reflect.Method;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.client.ClientHttpRequest;

public interface UpstreamHttpRequestInitializer {

    default void preprocess(Class<?> k, Map<Method, EndpointDescriptor> endpoints) {
    }

    void initialize(InvocationContext invocation, ClientHttpRequest request);

}
