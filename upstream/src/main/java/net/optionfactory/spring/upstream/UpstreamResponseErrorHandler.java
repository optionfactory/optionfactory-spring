package net.optionfactory.spring.upstream;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.Expressions;

/// Decides whether a response is an error, and turns it into an exception.
///
/// This is the upstream counterpart of spring's `ResponseErrorHandler`. The handlers of a client are
/// consulted in order and the first one whose [#hasError] returns true handles the response, the
/// others are skipped: the ones registered through [UpstreamBuilder#responseErrorHandler] come
/// first, followed by the built-in
/// [net.optionfactory.spring.upstream.errors.UpstreamErrorOnErrorStatusHandler] (any `4xx` or `5xx`)
/// and [net.optionfactory.spring.upstream.errors.UpstreamErrorOnResponseHandler]
/// (`@Upstream.ErrorOnResponse`). A registered handler can therefore replace the exception thrown
/// for an error status, or accept such a response by returning from [#handleError] without
/// throwing.
///
/// Handlers run when the response is about to be mapped, after the interceptors have returned.
public interface UpstreamResponseErrorHandler {

    /// Called once, when the client is built, before any request: the place to read the
    /// annotations of every endpoint and to parse their expressions, so that nothing is parsed per
    /// request. Does nothing by default.
    ///
    /// @param k the proxied interface, possibly a subinterface of the one declaring the methods
    /// @param expressions the parser and evaluation contexts of the client
    /// @param endpoints the endpoints of the client, by method
    default void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
    }

    /// @param invocation the invocation that caused the exchange
    /// @param request the request that was sent
    /// @param response the response received
    /// @return true when this handler claims the response as an error
    /// @throws IOException when the response cannot be read
    boolean hasError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException;

    /// Handles a response this handler claimed, usually by throwing an exception that the client
    /// method then throws. Returning normally lets the response be mapped as if it were not an
    /// error.
    ///
    /// @param invocation the invocation that caused the exchange
    /// @param request the request that was sent
    /// @param response the response received
    /// @throws IOException when the response cannot be read
    void handleError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException;

}
