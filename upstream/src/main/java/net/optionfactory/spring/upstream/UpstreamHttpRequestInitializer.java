package net.optionfactory.spring.upstream;

import java.lang.reflect.Method;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.http.client.ClientHttpRequest;

/// Initializes the requests of an upstream client before they are sent, typically to
/// authenticate them (see the `auth` package).
///
/// This is the upstream counterpart of spring's `ClientHttpRequestInitializer`, with the
/// invocation that caused the request at hand. Initializers run in registration order when the
/// request is created, before the body is written and before any [UpstreamHttpInterceptor]: the
/// interceptors and the logs see the headers they set, while the uri they see is the one built from
/// the method arguments, before interceptors such as the `@Upstream.QueryParam` one rewrite it.
///
/// One instance serves every invocation of the client, possibly concurrently.
public interface UpstreamHttpRequestInitializer {

    /// Called once, when the client is built, before any request: the place to read the
    /// annotations of every endpoint and to parse their expressions, so that nothing is parsed per
    /// request. Does nothing by default.
    ///
    /// @param k the proxied interface, possibly a subinterface of the one declaring the methods
    /// @param expressions the parser and evaluation contexts of the client
    /// @param endpoints the endpoints of the client, by method
    default void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
    }

    /// Initializes one request.
    ///
    /// @param invocation the invocation of the client method that caused the request
    /// @param request the request to initialize, whose headers can still be modified
    void initialize(InvocationContext invocation, ClientHttpRequest request);

}
