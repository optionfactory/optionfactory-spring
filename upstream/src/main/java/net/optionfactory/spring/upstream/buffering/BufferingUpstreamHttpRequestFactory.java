package net.optionfactory.spring.upstream.buffering;

import java.io.IOException;
import java.net.URI;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;

/// Wraps the requests of a request factory into [BufferingUpstreamHttpRequest]s, buffered according
/// to the [Buffering] of the invoked endpoint.
///
/// The mock and the HttpComponents request factories of
/// [net.optionfactory.spring.upstream.UpstreamBuilder] are wrapped with it.
public class BufferingUpstreamHttpRequestFactory implements UpstreamHttpRequestFactory {

    private final ClientHttpRequestFactory inner;

    /// @param inner the factory creating the actual requests
    public BufferingUpstreamHttpRequestFactory(ClientHttpRequestFactory inner) {
        this.inner = inner;
    }

    /// @param invocation the invocation in progress, whose buffering applies
    /// @param uri the request uri
    /// @param httpMethod the request method
    /// @return a buffering request wrapping the one of the inner factory
    /// @throws IOException when the inner factory fails
    @Override
    public ClientHttpRequest createRequest(InvocationContext invocation, URI uri, HttpMethod httpMethod) throws IOException {
        return new BufferingUpstreamHttpRequest(inner.createRequest(uri, httpMethod), invocation.buffering());
    }

}
