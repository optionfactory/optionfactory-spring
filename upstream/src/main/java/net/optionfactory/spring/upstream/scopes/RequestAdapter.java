package net.optionfactory.spring.upstream.scopes;

import java.net.URI;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;

public class RequestAdapter implements HttpRequest {

    private final RequestContext request;

    public RequestAdapter(RequestContext request) {
        this.request = request;
    }

    @Override
    public HttpMethod getMethod() {
        return request.method();
    }

    @Override
    public URI getURI() {
        return request.uri();
    }

    @Override
    public HttpHeaders getHeaders() {
        return request.headers();
    }

}
