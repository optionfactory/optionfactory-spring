package net.optionfactory.spring.upstream.scopes;

import java.util.function.Supplier;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestInitializer;

/// Exposes an [UpstreamHttpRequestInitializer] as a `ClientHttpRequestInitializer`, handing it
/// the invocation in progress.
public class RequestInitializerAdapter implements ClientHttpRequestInitializer {

    private final UpstreamHttpRequestInitializer inner;
    private final Supplier<InvocationContext> invocation;

    /// @param inner the initializer to adapt
    /// @param invocation supplies the invocation in progress
    public RequestInitializerAdapter(UpstreamHttpRequestInitializer inner, Supplier<InvocationContext> invocation) {
        this.inner = inner;
        this.invocation = invocation;
    }

    /// @param request the request to initialize, through the inner initializer
    @Override
    public void initialize(ClientHttpRequest request) {
        inner.initialize(invocation.get(), request);
    }

}
