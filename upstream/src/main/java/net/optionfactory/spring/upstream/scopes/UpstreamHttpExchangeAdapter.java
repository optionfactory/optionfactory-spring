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

public interface UpstreamHttpExchangeAdapter {

    default void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
    }

    boolean supportsRequestAttributes(InvocationContext invocation);

    void exchange(InvocationContext invocation, HttpRequestValues requestValues);

    HttpHeaders exchangeForHeaders(InvocationContext invocation, HttpRequestValues values);

    @Nullable
    <T> T exchangeForBody(InvocationContext invocation, HttpRequestValues values, ParameterizedTypeReference<T> bodyType);

    ResponseEntity<Void> exchangeForBodilessEntity(InvocationContext invocation, HttpRequestValues values);

    <T> ResponseEntity<T> exchangeForEntity(InvocationContext invocation, HttpRequestValues values, ParameterizedTypeReference<T> bodyType);

    public interface HttpRequestValuesTransformer {

        default void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {

        }

        HttpRequestValues transform(InvocationContext invocation, HttpRequestValues requestValues);

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

    public class Chain implements UpstreamHttpExchangeAdapter {

        private final HttpExchangeAdapter inner;
        private final List<HttpRequestValuesTransformer> rvts;

        public Chain(HttpExchangeAdapter inner, List<HttpRequestValuesTransformer> rvts) {
            this.inner = inner;
            this.rvts = rvts;
        }

        @Override
        public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
            for (var rvt : rvts) {
                rvt.preprocess(k, expressions, endpoints);
            }
        }

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

        @Override
        public void exchange(InvocationContext invocation, HttpRequestValues requestValues) {
            inner.exchange(transform(invocation, requestValues));
        }

        @Override
        public HttpHeaders exchangeForHeaders(InvocationContext invocation, HttpRequestValues values) {
            return inner.exchangeForHeaders(transform(invocation, values));
        }

        @Override
        public <T> T exchangeForBody(InvocationContext invocation, HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
            return inner.exchangeForBody(transform(invocation, values), bodyType);
        }

        @Override
        public ResponseEntity<Void> exchangeForBodilessEntity(InvocationContext invocation, HttpRequestValues values) {
            return inner.exchangeForBodilessEntity(transform(invocation, values));
        }

        @Override
        public <T> ResponseEntity<T> exchangeForEntity(InvocationContext invocation, HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
            return inner.exchangeForEntity(transform(invocation, values), bodyType);
        }

    }

}
