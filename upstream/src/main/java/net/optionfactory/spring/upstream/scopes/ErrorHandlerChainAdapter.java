package net.optionfactory.spring.upstream.scopes;

import java.io.IOException;
import java.util.List;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

/// Exposes the [UpstreamResponseErrorHandler]s of a client as the one `RestClient` error handler
/// consulted on every response, handing them the invocation, request and response contexts: the
/// first handler whose `hasError` returns true handles the response and the others are skipped;
/// when none does, the response is mapped.
///
/// A single handler is needed because the `RestClient` decides whether a handler applies from the
/// status code alone, while ours can look at the whole response (`@Upstream.ErrorOnResponse`).
///
/// The contexts are taken from the response, which must be the [ResponseAdapter] returned by the
/// [InterceptorChainAdapter]; any other response fails with a `ClassCastException`. Clients built
/// by `UpstreamBuilder` always install the chain, so their responses always are.
public class ErrorHandlerChainAdapter implements RestClient.ResponseSpec.ErrorHandler {

    private final List<UpstreamResponseErrorHandler> handlers;

    /// @param handlers the handlers, in the order they are consulted
    public ErrorHandlerChainAdapter(List<UpstreamResponseErrorHandler> handlers) {
        this.handlers = handlers;
    }

    /// @param request the request, unused: the request context is used instead
    /// @param response the [ResponseAdapter] of the exchange
    /// @throws IOException when a handler fails reading the response
    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        final var r = (ResponseAdapter) response;
        for (final var handler : handlers) {
            if (handler.hasError(r.invocation(), r.request(), r.response())) {
                handler.handleError(r.invocation(), r.request(), r.response());
                return;
            }
        }
    }

}
