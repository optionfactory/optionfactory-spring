package net.optionfactory.spring.upstream.scopes;

import java.io.IOException;
import java.io.InputStream;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

public record ResponseAdapter(
        InvocationContext invocation,
        RequestContext request,
        ResponseContext response,
        ClientHttpResponse inner) implements ClientHttpResponse {

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return response.status();
    }

    @Override
    public String getStatusText() throws IOException {
        return response.statusText();
    }

    @Override
    public HttpHeaders getHeaders() {
        return response.headers();
    }

    @Override
    public InputStream getBody() throws IOException {
        return response.body().inputStream();
    }

    @Override
    public void close() {
        inner.close();
    }
}
