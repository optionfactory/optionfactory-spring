package net.optionfactory.spring.upstream.scopes;

import java.io.IOException;
import java.net.URI;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

/// Exposes an [UpstreamResponseErrorHandler] as a `ResponseErrorHandler`, handing it the
/// invocation, request and response contexts.
///
/// The contexts are taken from the response, which must be the [ResponseAdapter] returned by the
/// [InterceptorChainAdapter]; any other response fails with a `ClassCastException`. Clients built
/// by `UpstreamBuilder` always install the chain, so their responses always are.
public class ResponseErrorHandlerAdapter implements ResponseErrorHandler {

    private final UpstreamResponseErrorHandler inner;

    /// @param inner the handler to adapt
    public ResponseErrorHandlerAdapter(UpstreamResponseErrorHandler inner) {
        this.inner = inner;
    }

    /// @param response the [ResponseAdapter] of the exchange
    /// @return whether the inner handler sees an error
    /// @throws IOException when the inner handler fails reading the response
    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        final var r = (ResponseAdapter) response;
        return inner.hasError(r.invocation(), r.request(), r.response());
    }

    /// @param url the request uri, unused: the request context is used instead
    /// @param method the request method, unused
    /// @param response the [ResponseAdapter] of the exchange
    /// @throws IOException when the inner handler fails reading the response
    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
        final var r = (ResponseAdapter) response;
        inner.handleError(r.invocation(), r.request(), r.response());
    }

}
