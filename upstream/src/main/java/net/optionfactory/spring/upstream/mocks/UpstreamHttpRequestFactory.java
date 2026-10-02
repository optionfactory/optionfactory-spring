package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;

/// Creates the requests of an upstream client, like a `ClientHttpRequestFactory` that also knows
/// which invocation the request is for.
///
/// Configured with `UpstreamBuilder.requestFactory(UpstreamHttpRequestFactory)`: the builder calls
/// [#preprocess(Class, Expressions, Map)] once while building the client, then adapts the factory
/// so that each request is created with the invocation in progress on the calling thread.
public interface UpstreamHttpRequestFactory {

    /// Inspects the client once, at build time, typically to read the endpoints' annotations. Does
    /// nothing by default.
    ///
    /// @param k the client interface
    /// @param expressions the parser for the expressions found in annotations
    /// @param endpoints the endpoints of the client, by method
    default void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
    }
    
    
    /// @param invocation the invocation the request is created for
    /// @param uri the request uri
    /// @param httpMethod the request method
    /// @return a new request
    /// @throws IOException when the request cannot be created
    ClientHttpRequest createRequest(InvocationContext invocation, URI uri, HttpMethod httpMethod) throws IOException;

}
