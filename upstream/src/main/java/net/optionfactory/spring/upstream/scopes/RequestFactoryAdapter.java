package net.optionfactory.spring.upstream.scopes;

import java.io.IOException;
import java.net.URI;
import java.util.function.Supplier;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;

public class RequestFactoryAdapter implements ClientHttpRequestFactory {

    private final UpstreamHttpRequestFactory inner;
    private final Supplier<InvocationContext> invocation;

    public RequestFactoryAdapter(UpstreamHttpRequestFactory inner, Supplier<InvocationContext> invocation) {
        this.inner = inner;
        this.invocation = invocation;
    }

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
        return inner.createRequest(invocation.get(), uri, httpMethod);
    }

}
