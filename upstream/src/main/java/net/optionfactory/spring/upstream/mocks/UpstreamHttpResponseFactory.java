package net.optionfactory.spring.upstream.mocks;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;

/// Creates the responses of a mocked upstream client.
///
/// Set with [MocksCustomizer#responseFactory(UpstreamHttpResponseFactory)], where a lambda is
/// enough, or implemented by [MockResourcesUpstreamHttpResponseFactory], the default.
///
/// ```java
/// .requestFactoryMock(c -> c.responseFactory((invocation, uri, method, headers) -> {
///     final var h = new HttpHeaders();
///     h.setContentType(MediaType.APPLICATION_JSON);
///     return new MockClientHttpResponse(HttpStatus.OK, "OK", h, new ByteArrayResource("{}".getBytes(StandardCharsets.UTF_8)));
/// }))
/// ```
public interface UpstreamHttpResponseFactory {

    /// Inspects the client once, at build time, typically to read the endpoints' annotations. Does
    /// nothing by default.
    ///
    /// @param k the client interface
    /// @param expressions the parser for the expressions found in annotations
    /// @param endpoints the endpoints of the client, by method
    default void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
    }


    /// Called on every exchange, possibly by concurrent threads.
    ///
    /// @param invocation the invocation being answered
    /// @param uri the request uri
    /// @param method the request method
    /// @param headers the request headers
    /// @return the response of the exchange
    ClientHttpResponse create(InvocationContext invocation, URI uri, HttpMethod method, HttpHeaders headers);

}
