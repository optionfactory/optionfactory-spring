package net.optionfactory.spring.upstream.scopes;

import java.util.function.Supplier;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestInitializer;

public class RequestInitializerAdapter implements ClientHttpRequestInitializer {

    private final UpstreamHttpRequestInitializer inner;
    private final Supplier<InvocationContext> invocation;

    public RequestInitializerAdapter(UpstreamHttpRequestInitializer inner, Supplier<InvocationContext> invocation) {
        this.inner = inner;
        this.invocation = invocation;
    }

    @Override
    public void initialize(ClientHttpRequest request) {
        inner.initialize(invocation.get(), request);
    }

}
