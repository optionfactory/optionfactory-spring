package net.optionfactory.spring.upstream.scopes;

import java.io.IOException;
import java.net.URI;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

public class ResponseErrorHandlerAdapter implements ResponseErrorHandler {

    private final UpstreamResponseErrorHandler inner;

    public ResponseErrorHandlerAdapter(UpstreamResponseErrorHandler inner) {
        this.inner = inner;
    }

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        final var r = (ResponseAdapter) response;
        return inner.hasError(r.invocation(), r.request(), r.response());
    }

    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
        final var r = (ResponseAdapter) response;
        inner.handleError(r.invocation(), r.request(), r.response());
    }

}
