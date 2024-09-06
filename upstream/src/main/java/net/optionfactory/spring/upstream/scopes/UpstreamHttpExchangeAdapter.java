package net.optionfactory.spring.upstream.scopes;

import java.lang.reflect.Method;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
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

    public static HttpRequestValues.Builder valuesBuilder(HttpRequestValues values) {
        final var builder = HttpRequestValues.builder()
                .setHttpMethod(values.getHttpMethod())
                .setUri(values.getUri())
                .setUriBuilderFactory(values.getUriBuilderFactory())
                .setUriTemplate(values.getUriTemplate());
        for (final var uriVar : values.getUriVariables().entrySet()) {
            builder.setUriVariable(uriVar.getKey(), uriVar.getValue());
        }
        for (final var header : values.getHeaders().entrySet()) {
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
