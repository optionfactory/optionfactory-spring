package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.net.URI;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor.InvocationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;

public class MockUpstreamRequestFactory implements UpstreamHttpRequestFactory {

    private final UpstreamHttpResponseFactory strategy;

    public MockUpstreamRequestFactory(UpstreamHttpResponseFactory strategy) {
        this.strategy = strategy;
    }

    @Override
    public ClientHttpRequest createRequest(InvocationContext ctx, URI uri, HttpMethod httpMethod) throws IOException {
        return new MockClientHttpRequest(uri, httpMethod, strategy, ctx);
    }

}
