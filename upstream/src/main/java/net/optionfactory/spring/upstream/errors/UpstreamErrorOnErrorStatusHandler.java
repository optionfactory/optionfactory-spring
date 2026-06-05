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

public class UpstreamErrorOnErrorStatusHandler implements UpstreamResponseErrorHandler {

    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
    }

    @Override
    public boolean hasError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException {
        return response.status().isError();
    }

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
