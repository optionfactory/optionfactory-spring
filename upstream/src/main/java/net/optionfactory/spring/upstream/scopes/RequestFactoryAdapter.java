package net.optionfactory.spring.upstream.scopes;

import java.io.IOException;
import java.net.URI;
import java.util.function.Supplier;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;

/// Exposes an [UpstreamHttpRequestFactory] as a `ClientHttpRequestFactory`, handing it the
/// invocation in progress.
public class RequestFactoryAdapter implements ClientHttpRequestFactory {

    private final UpstreamHttpRequestFactory inner;
    private final Supplier<InvocationContext> invocation;

    /// @param inner the factory to adapt
    /// @param invocation supplies the invocation in progress
    public RequestFactoryAdapter(UpstreamHttpRequestFactory inner, Supplier<InvocationContext> invocation) {
        this.inner = inner;
        this.invocation = invocation;
    }

    /// @param uri the request uri
    /// @param httpMethod the request method
    /// @return the request the inner factory creates for the current invocation
    /// @throws IOException when the request cannot be created
    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
        return inner.createRequest(invocation.get(), uri, httpMethod);
    }

}
