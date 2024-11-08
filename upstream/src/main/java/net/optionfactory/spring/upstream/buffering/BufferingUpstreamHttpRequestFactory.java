package net.optionfactory.spring.upstream.buffering;

import java.io.IOException;
import java.net.URI;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;

public class BufferingUpstreamHttpRequestFactory implements UpstreamHttpRequestFactory {

    private final ClientHttpRequestFactory inner;

    public BufferingUpstreamHttpRequestFactory(ClientHttpRequestFactory inner) {
        this.inner = inner;
    }

    @Override
    public ClientHttpRequest createRequest(InvocationContext invocation, URI uri, HttpMethod httpMethod) throws IOException {
        return new BufferingUpstreamHttpRequest(inner.createRequest(uri, httpMethod), invocation.buffering());
    }

}
