package net.optionfactory.spring.upstream.scopes;

import java.io.IOException;
import java.io.InputStream;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

/// The `ClientHttpResponse` the [InterceptorChainAdapter] returns to the `RestClient`: it serves
/// the [ResponseContext] produced by the interceptors, and carries the invocation and request
/// contexts on to the [ResponseErrorHandlerAdapter].
///
/// @param invocation the invocation the exchange belongs to
/// @param request the request that was executed
/// @param response the response context, whose status, headers and body are served
/// @param inner the response actually received, closed by [#close()]
public record ResponseAdapter(
        InvocationContext invocation,
        RequestContext request,
        ResponseContext response,
        ClientHttpResponse inner) implements ClientHttpResponse {

    /// @return the status of the response context
    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return response.status();
    }

    /// @return the reason phrase of the response context
    @Override
    public String getStatusText() throws IOException {
        return response.statusText();
    }

    /// @return the headers of the response context
    @Override
    public HttpHeaders getHeaders() {
        return response.headers();
    }

    /// @return the body of the response context
    @Override
    public InputStream getBody() throws IOException {
        return response.body().getInputStream();
    }

    /// Closes the response actually received.
    @Override
    public void close() {
        inner.close();
    }
}
