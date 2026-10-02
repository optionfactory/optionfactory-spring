package net.optionfactory.spring.upstream.scopes;

import java.net.URI;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;

/// Exposes a [RequestContext] as the `HttpRequest` a `ClientHttpRequestExecution` executes.
///
/// Every accessor returns the context's own instance, nothing is copied.
public class RequestAdapter implements HttpRequest {

    private final RequestContext request;

    /// @param request the request context to expose
    public RequestAdapter(RequestContext request) {
        this.request = request;
    }

    /// @return the context method
    @Override
    public HttpMethod getMethod() {
        return request.method();
    }

    /// @return the context uri
    @Override
    public URI getURI() {
        return request.uri();
    }

    /// @return the context headers
    @Override
    public HttpHeaders getHeaders() {
        return request.headers();
    }

    /// @return the context attributes
    @Override
    public Map<String, Object> getAttributes() {
        return request.attributes();
    }

}
