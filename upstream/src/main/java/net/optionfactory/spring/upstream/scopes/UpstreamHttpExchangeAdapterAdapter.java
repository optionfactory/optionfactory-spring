package net.optionfactory.spring.upstream.scopes;

import java.util.function.Supplier;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpRequestValues;

/// Exposes an [UpstreamHttpExchangeAdapter] as the `HttpExchangeAdapter` of an
/// `HttpServiceProxyFactory`, handing every call the invocation in progress.
public class UpstreamHttpExchangeAdapterAdapter implements HttpExchangeAdapter {

    private final UpstreamHttpExchangeAdapter inner;
    private final Supplier<InvocationContext> invocation;

    /// @param inner the adapter to expose
    /// @param invocation supplies the invocation in progress
    public UpstreamHttpExchangeAdapterAdapter(UpstreamHttpExchangeAdapter inner, Supplier<InvocationContext> invocation) {
        this.inner = inner;
        this.invocation = invocation;
    }

    /// @return what the inner adapter answers for the current invocation
    @Override
    public boolean supportsRequestAttributes() {
        return inner.supportsRequestAttributes(invocation.get());
    }

    /// @param requestValues the request values
    @Override
    public void exchange(HttpRequestValues requestValues) {
        inner.exchange(invocation.get(), requestValues);
    }

    /// @param requestValues the request values
    /// @return the response headers
    @Override
    public HttpHeaders exchangeForHeaders(HttpRequestValues requestValues) {
        return inner.exchangeForHeaders(invocation.get(), requestValues);
    }

    /// @param <T> the body type
    /// @param requestValues the request values
    /// @param bodyType the body type
    /// @return the response body, possibly `null`
    @Override
    public <T> T exchangeForBody(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
        return inner.exchangeForBody(invocation.get(), requestValues, bodyType);
    }

    /// @param requestValues the request values
    /// @return the response status and headers
    @Override
    public ResponseEntity<Void> exchangeForBodilessEntity(HttpRequestValues requestValues) {
        return inner.exchangeForBodilessEntity(invocation.get(), requestValues);
    }

    /// @param <T> the body type
    /// @param requestValues the request values
    /// @param bodyType the body type
    /// @return the response entity
    @Override
    public <T> ResponseEntity<T> exchangeForEntity(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
        return inner.exchangeForEntity(invocation.get(), requestValues, bodyType);
    }

}
