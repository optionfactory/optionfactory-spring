package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

public class MockClientHttpRequest implements ClientHttpRequest {

    private final URI uri;
    private final HttpMethod method;
    private final HttpHeaders headers = new HttpHeaders();
    private final UpstreamHttpResponseFactory strategy;
    private final UpstreamHttpInterceptor.InvocationContext ctx;

    public MockClientHttpRequest(URI uri, HttpMethod method, UpstreamHttpResponseFactory strategy, UpstreamHttpInterceptor.InvocationContext ctx) {
        this.uri = uri;
        this.method = method;
        this.strategy = strategy;
        this.ctx = ctx;
    }

    @Override
    public ClientHttpResponse execute() throws IOException {
        return strategy.create(ctx, uri, method, headers);
    }

    @Override
    public HttpMethod getMethod() {
        return method;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public OutputStream getBody() throws IOException {
        return OutputStream.nullOutputStream();
    }

}
