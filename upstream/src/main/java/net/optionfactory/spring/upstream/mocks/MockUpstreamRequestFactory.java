package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;

public class MockUpstreamRequestFactory implements UpstreamHttpRequestFactory {

    private final UpstreamHttpResponseFactory strategy;

    public MockUpstreamRequestFactory(UpstreamHttpResponseFactory strategy) {
        this.strategy = strategy;
    }

    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        strategy.preprocess(k, expressions, endpoints);
    }

    @Override
    public ClientHttpRequest createRequest(InvocationContext invocation, URI uri, HttpMethod httpMethod) throws IOException {
        return new MockClientHttpRequest(uri, httpMethod, strategy, invocation);
    }

}
