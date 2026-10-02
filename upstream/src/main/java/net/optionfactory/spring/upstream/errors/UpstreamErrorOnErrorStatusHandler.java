package net.optionfactory.spring.upstream.errors;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.Expressions;

/// Turns every `4xx` and `5xx` response into a [RestClientUpstreamException] whose reason is the
/// status code and text, e.g. `404 Not Found`.
///
/// Always registered by [net.optionfactory.spring.upstream.UpstreamBuilder], after the handlers
/// registered by the application and before [UpstreamErrorOnResponseHandler].
public class UpstreamErrorOnErrorStatusHandler implements UpstreamResponseErrorHandler {

    /// Does nothing: the handler needs no configuration.
    ///
    /// @param k the proxied interface
    /// @param expressions the expressions of the client
    /// @param endpoints the endpoints of the client, by method
    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
    }

    /// @param invocation the invocation
    /// @param request the request sent
    /// @param response the response received
    /// @return true for the `4xx` and `5xx` statuses
    @Override
    public boolean hasError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException {
        return response.status().isError();
    }

    /// @param invocation the invocation
    /// @param request the request sent
    /// @param response the response received, whose body is read into the exception
    /// @throws RestClientUpstreamException always
    @Override
    public void handleError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException {
        final var reason = response.status().value() + " " + response.statusText();

        throw new RestClientUpstreamException(
                invocation.converters(),
                invocation.endpoint().upstream(),
                invocation.endpoint().name(),
                reason,
                response.status(),
                response.statusText(),
                response.headers(),
                response.body().bytes()
        );
    }

}
