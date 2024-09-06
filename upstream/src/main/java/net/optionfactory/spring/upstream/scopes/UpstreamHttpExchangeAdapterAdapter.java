package net.optionfactory.spring.upstream.scopes;

import java.util.function.Supplier;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpRequestValues;

public class UpstreamHttpExchangeAdapterAdapter implements HttpExchangeAdapter {

    private final UpstreamHttpExchangeAdapter inner;
    private final Supplier<InvocationContext> invocation;

    public UpstreamHttpExchangeAdapterAdapter(UpstreamHttpExchangeAdapter inner, Supplier<InvocationContext> invocation) {
        this.inner = inner;
        this.invocation = invocation;
    }

    @Override
    public boolean supportsRequestAttributes() {
        return inner.supportsRequestAttributes(invocation.get());
    }

    @Override
    public void exchange(HttpRequestValues requestValues) {
        inner.exchange(invocation.get(), requestValues);
    }

    @Override
    public HttpHeaders exchangeForHeaders(HttpRequestValues requestValues) {
        return inner.exchangeForHeaders(invocation.get(), requestValues);
    }

    @Override
    public <T> T exchangeForBody(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
        return inner.exchangeForBody(invocation.get(), requestValues, bodyType);
    }

    @Override
    public ResponseEntity<Void> exchangeForBodilessEntity(HttpRequestValues requestValues) {
        return inner.exchangeForBodilessEntity(invocation.get(), requestValues);
    }

    @Override
    public <T> ResponseEntity<T> exchangeForEntity(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
        return inner.exchangeForEntity(invocation.get(), requestValues, bodyType);
    }

}
