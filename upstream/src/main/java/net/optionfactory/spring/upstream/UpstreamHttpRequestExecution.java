package net.optionfactory.spring.upstream;

import java.io.IOException;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;

/// The rest of an [UpstreamHttpInterceptor] chain: the following interceptors and, at its end, the
/// actual exchange.
public interface UpstreamHttpRequestExecution {

    /// Proceeds with the exchange.
    ///
    /// @param invocation the invocation being served
    /// @param request the request to send, which may differ from the one the interceptor received
    /// @return the response of the exchange; its body may be readable only once, see
    /// [ResponseContext.BodySource]
    /// @throws IOException when the exchange fails
    ResponseContext execute(InvocationContext invocation, RequestContext request) throws IOException;
}
