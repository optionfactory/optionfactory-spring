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

/// A `ClientHttpRequest` that never reaches the network: executing it asks an
/// [UpstreamHttpResponseFactory] for the response.
///
/// Created by [MockUpstreamRequestFactory] for each exchange of a client built with
/// `UpstreamBuilder.requestFactoryMock`. The body written to the request is discarded, so a response
/// factory decides from the invocation, the uri, the method and the headers only; interceptors
/// still see the body, since they run before the request is created.
public class MockClientHttpRequest implements ClientHttpRequest {

    private final URI uri;
    private final HttpMethod method;
    private final HttpHeaders headers = new HttpHeaders();
    private final UpstreamHttpResponseFactory strategy;
    private final InvocationContext invocation;
    private final Map<String, Object> attributes = new HashMap<>();

    /// @param uri the request uri
    /// @param method the request method
    /// @param strategy the factory producing the response on [#execute()]
    /// @param invocation the invocation the request belongs to, handed to `strategy`
    public MockClientHttpRequest(URI uri, HttpMethod method, UpstreamHttpResponseFactory strategy, InvocationContext invocation) {
        this.uri = uri;
        this.method = method;
        this.strategy = strategy;
        this.invocation = invocation;
    }

    /// @return the response the factory creates for this invocation, uri, method and the headers
    /// set on this request so far
    @Override
    public ClientHttpResponse execute() throws IOException {
        return strategy.create(invocation, uri, method, headers);
    }

    /// @return the method the request was created with
    @Override
    public HttpMethod getMethod() {
        return method;
    }

    /// @return the uri the request was created with
    @Override
    public URI getURI() {
        return uri;
    }

    /// @return a mutable map, initially empty, owned by this request
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /// @return the mutable headers, handed to the response factory on [#execute()]
    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    /// @return a stream discarding everything written to it
    @Override
    public OutputStream getBody() throws IOException {
        return OutputStream.nullOutputStream();
    }

}
