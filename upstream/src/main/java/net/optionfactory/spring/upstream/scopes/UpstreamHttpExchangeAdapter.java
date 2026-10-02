package net.optionfactory.spring.upstream.scopes;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpRequestValues;

/// The counterpart of spring's `HttpExchangeAdapter` that also receives the invocation in
/// progress: it performs the exchanges the client proxy describes as `HttpRequestValues`.
///
/// `UpstreamBuilder` uses a [Chain] over the `RestClient` adapter, so that the request values can be
/// transformed before each exchange.
public interface UpstreamHttpExchangeAdapter {

    /// Inspects the client once, at build time. Does nothing by default.
    ///
    /// @param k the client interface
    /// @param expressions the parser for the expressions found in annotations
    /// @param endpoints the endpoints of the client, by method
    default void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
    }

    /// @param invocation the invocation in progress
    /// @return whether request attributes are supported, see
    /// `HttpExchangeAdapter.supportsRequestAttributes`
    boolean supportsRequestAttributes(InvocationContext invocation);

    /// Performs an exchange, ignoring the response body.
    ///
    /// @param invocation the invocation in progress
    /// @param requestValues the request values
    void exchange(InvocationContext invocation, HttpRequestValues requestValues);

    /// @param invocation the invocation in progress
    /// @param values the request values
    /// @return the response headers
    HttpHeaders exchangeForHeaders(InvocationContext invocation, HttpRequestValues values);

    /// @param <T> the body type
    /// @param invocation the invocation in progress
    /// @param values the request values
    /// @param bodyType the body type
    /// @return the response body, `null` when there is none
    @Nullable
    <T> T exchangeForBody(InvocationContext invocation, HttpRequestValues values, ParameterizedTypeReference<T> bodyType);

    /// @param invocation the invocation in progress
    /// @param values the request values
    /// @return the response status and headers
    ResponseEntity<Void> exchangeForBodilessEntity(InvocationContext invocation, HttpRequestValues values);

    /// @param <T> the body type
    /// @param invocation the invocation in progress
    /// @param values the request values
    /// @param bodyType the body type
    /// @return the response entity
    <T> ResponseEntity<T> exchangeForEntity(InvocationContext invocation, HttpRequestValues values, ParameterizedTypeReference<T> bodyType);

    /// Rewrites the request values of an exchange before it is performed, e.g. to wrap the body or
    /// to set uri variables.
    ///
    /// Registered with `UpstreamBuilder.requestValuesTransformer`; the transformers run in
    /// registration order, after the built-in one handling `@Upstream.PathVariable`.
    ///
    /// ```java
    /// public HttpRequestValues transform(InvocationContext invocation, HttpRequestValues values) {
    ///     final var builder = HttpRequestValuesTransformer.valuesBuilder(values);
    ///     builder.setBodyValue(new Envelope<>(values.getBodyValue()));
    ///     return builder.build();
    /// }
    /// ```
    public interface HttpRequestValuesTransformer {

        /// Inspects the client once, at build time. Does nothing by default.
        ///
        /// @param k the client interface
        /// @param expressions the parser for the expressions found in annotations
        /// @param endpoints the endpoints of the client, by method
        default void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {

        }

        /// @param invocation the invocation in progress
        /// @param requestValues the values to transform
        /// @return the values to use, possibly `requestValues` itself
        HttpRequestValues transform(InvocationContext invocation, HttpRequestValues requestValues);

        /// Starts a builder from existing request values, to change some of them.
        ///
        /// It copies the method, the uri, the uri builder factory, the uri template, the uri
        /// variables, the headers, the cookies, the attributes and the body value. It does not copy
        /// the api version nor the body value type, which are therefore lost unless set again.
        ///
        /// @param values the values to copy
        /// @return a builder holding a copy of the values
        public static HttpRequestValues.Builder valuesBuilder(HttpRequestValues values) {
            final var builder = HttpRequestValues.builder()
                    .setHttpMethod(values.getHttpMethod())
                    .setUri(values.getUri())
                    .setUriBuilderFactory(values.getUriBuilderFactory())
                    .setUriTemplate(values.getUriTemplate());

            for (final var uriVar : values.getUriVariables().entrySet()) {
                builder.setUriVariable(uriVar.getKey(), uriVar.getValue());
            }
            for (final var header : values.getHeaders().headerSet()) {
                builder.addHeader(header.getKey(), header.getValue().toArray(i -> new String[i]));
            }
            for (final var cookie : values.getCookies().entrySet()) {
                builder.addCookie(cookie.getKey(), cookie.getValue().toArray(i -> new String[i]));
            }
            for (final var attribute : values.getAttributes().entrySet()) {
                builder.addAttribute(attribute.getKey(), attribute.getValue());
            }
            builder.setBodyValue(values.getBodyValue());
            return builder;

        }
    }

    /// Applies [HttpRequestValuesTransformer]s, in order, to the request values of each exchange,
    /// then delegates the exchange to a spring `HttpExchangeAdapter`.
    public class Chain implements UpstreamHttpExchangeAdapter {

        private final HttpExchangeAdapter inner;
        private final List<HttpRequestValuesTransformer> rvts;

        /// @param inner the adapter performing the exchanges
        /// @param rvts the transformers, in application order
        public Chain(HttpExchangeAdapter inner, List<HttpRequestValuesTransformer> rvts) {
            this.inner = inner;
            this.rvts = rvts;
        }

        /// Lets every transformer preprocess the client.
        ///
        /// @param k the client interface
        /// @param expressions the parser for the expressions found in annotations
        /// @param endpoints the endpoints of the client, by method
        @Override
        public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
            for (var rvt : rvts) {
                rvt.preprocess(k, expressions, endpoints);
            }
        }

        /// @param invocation the invocation in progress
        /// @return what the inner adapter answers
        @Override
        public boolean supportsRequestAttributes(InvocationContext invocation) {
            return inner.supportsRequestAttributes();
        }

        private HttpRequestValues transform(InvocationContext invocation, HttpRequestValues requestValues) {
            for (HttpRequestValuesTransformer rvt : rvts) {
                requestValues = rvt.transform(invocation, requestValues);
            }
            return requestValues;
        }

        /// @param invocation the invocation in progress
        /// @param requestValues the request values, transformed before the exchange
        @Override
        public void exchange(InvocationContext invocation, HttpRequestValues requestValues) {
            inner.exchange(transform(invocation, requestValues));
        }

        /// @param invocation the invocation in progress
        /// @param values the request values, transformed before the exchange
        /// @return the response headers
        @Override
        public HttpHeaders exchangeForHeaders(InvocationContext invocation, HttpRequestValues values) {
            return inner.exchangeForHeaders(transform(invocation, values));
        }

        /// @param <T> the body type
        /// @param invocation the invocation in progress
        /// @param values the request values, transformed before the exchange
        /// @param bodyType the body type
        /// @return the response body, possibly `null`
        @Override
        public <T> T exchangeForBody(InvocationContext invocation, HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
            return inner.exchangeForBody(transform(invocation, values), bodyType);
        }

        /// @param invocation the invocation in progress
        /// @param values the request values, transformed before the exchange
        /// @return the response status and headers
        @Override
        public ResponseEntity<Void> exchangeForBodilessEntity(InvocationContext invocation, HttpRequestValues values) {
            return inner.exchangeForBodilessEntity(transform(invocation, values));
        }

        /// @param <T> the body type
        /// @param invocation the invocation in progress
        /// @param values the request values, transformed before the exchange
        /// @param bodyType the body type
        /// @return the response entity
        @Override
        public <T> ResponseEntity<T> exchangeForEntity(InvocationContext invocation, HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
            return inner.exchangeForEntity(transform(invocation, values), bodyType);
        }

    }

}
