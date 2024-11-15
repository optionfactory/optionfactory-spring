package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

public class MockClientHttpRequest implements ClientHttpRequest {

    private final URI uri;
    private final HttpMethod method;
    private final HttpHeaders headers = new HttpHeaders();
    private final UpstreamHttpResponseFactory strategy;
    private final InvocationContext invocation;
    private final Map<String, Object> attributes = new HashMap<>();

    public MockClientHttpRequest(URI uri, HttpMethod method, UpstreamHttpResponseFactory strategy, InvocationContext invocation) {
        this.uri = uri;
        this.method = method;
        this.strategy = strategy;
        this.invocation = invocation;
    }

    @Override
    public ClientHttpResponse execute() throws IOException {
        return strategy.create(invocation, uri, method, headers);
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
    public Map<String, Object> getAttributes() {
        return attributes;
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
