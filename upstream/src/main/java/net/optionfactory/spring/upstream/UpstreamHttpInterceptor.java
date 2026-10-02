package net.optionfactory.spring.upstream;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.Expressions;

/// Intercepts the exchanges of an upstream client, seeing the request about to be sent and the
/// response received, together with the invocation that caused them.
///
/// This is the upstream counterpart of spring's `ClientHttpRequestInterceptor`: the interceptors
/// of a client form a chain, each one calling [UpstreamHttpRequestExecution#execute] to proceed
/// and possibly passing on a different [RequestContext] (e.g. one with another uri). The
/// interceptors registered through [UpstreamBuilder#interceptor] run first, in registration
/// order, and wrap the built-in ones, which (in order) add the `@Upstream.Header`, `@Upstream.Cookie`
/// and `@Upstream.QueryParam` values, log the exchange, and raise the alerts. A registered
/// interceptor therefore sees the request without the annotated values, and the response once it
/// has been logged and checked for alerts.
///
/// One instance serves every invocation of the client, possibly concurrently: per-endpoint state
/// is computed in [#preprocess] and must be read-only afterwards.
///
/// ```java
/// public class TimingInterceptor implements UpstreamHttpInterceptor {
///
///     @Override
///     public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
///         final var response = execution.execute(invocation, request);
///         metrics.record(invocation.endpoint().name(), Duration.between(request.at(), response.at()));
///         return response;
///     }
/// }
/// ```
public interface UpstreamHttpInterceptor {

    /// Called once, when the client is built, before any request: the place to read the
    /// annotations of every endpoint and to parse their expressions, so that nothing is parsed per
    /// request. Does nothing by default.
    ///
    /// @param k the proxied interface, possibly a subinterface of the one declaring the methods
    /// @param expressions the parser and evaluation contexts of the client
    /// @param endpoints the endpoints of the client, by method
    default void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
    }

    /// Intercepts one exchange.
    ///
    /// @param invocation the invocation of the client method that caused the exchange
    /// @param request the request about to be sent; its headers are mutable and shared with the
    /// rest of the chain
    /// @param execution the rest of the chain, ending with the actual exchange
    /// @return the response, usually the one returned by `execution`
    /// @throws IOException when the exchange fails
    ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException;

}
