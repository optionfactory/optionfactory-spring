package net.optionfactory.spring.upstream;

import java.lang.reflect.Method;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.http.client.ClientHttpRequest;

public interface UpstreamHttpRequestInitializer {

    default void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
    }

    void initialize(InvocationContext invocation, ClientHttpRequest request);

}
