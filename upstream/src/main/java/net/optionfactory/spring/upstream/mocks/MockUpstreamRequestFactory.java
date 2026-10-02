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

/// An [UpstreamHttpRequestFactory] creating [MockClientHttpRequest]s, whose responses come from an
/// [UpstreamHttpResponseFactory] instead of the network.
///
/// Normally configured through `UpstreamBuilder.requestFactoryMock`, which also wraps it to apply
/// the response buffering the endpoint's return type calls for, as with a real request factory.
public class MockUpstreamRequestFactory implements UpstreamHttpRequestFactory {

    private final UpstreamHttpResponseFactory strategy;

    /// @param strategy creates the response of every request
    public MockUpstreamRequestFactory(UpstreamHttpResponseFactory strategy) {
        this.strategy = strategy;
    }

    /// Lets the response factory preprocess the endpoints.
    ///
    /// @param k the client interface
    /// @param expressions the expressions parser
    /// @param endpoints the endpoints of the client, by method
    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        strategy.preprocess(k, expressions, endpoints);
    }

    /// @param invocation the current invocation
    /// @param uri the request uri
    /// @param httpMethod the request method
    /// @return a request answered by the response factory when executed
    @Override
    public ClientHttpRequest createRequest(InvocationContext invocation, URI uri, HttpMethod httpMethod) throws IOException {
        return new MockClientHttpRequest(uri, httpMethod, strategy, invocation);
    }

}
