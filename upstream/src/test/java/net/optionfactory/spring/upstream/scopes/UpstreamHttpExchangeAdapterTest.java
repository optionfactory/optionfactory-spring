package net.optionfactory.spring.upstream.scopes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.scopes.ExchangeAdapterClient.Wrapper;
import net.optionfactory.spring.upstream.scopes.UpstreamHttpExchangeAdapter.HttpRequestValuesTransformer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpRequestValues;
import tools.jackson.databind.json.JsonMapper;

public class UpstreamHttpExchangeAdapterTest {

    @Test
    public void canAdaptRequestBody() {

        final var capturedBody = new AtomicReference<String>();

        final var client = UpstreamBuilder
                .create(ExchangeAdapterClient.class)
                .json(new JsonMapper())
                .interceptor((invocation, request, execution) -> {
                    capturedBody.set(new String(request.body(), StandardCharsets.UTF_8));
                    return execution.execute(invocation, request);
                })
                .requestValuesTransformer(new AddWrapperToRequest())
                .requestFactoryMock(c -> {
                    c.response(HttpStatus.OK, MediaType.APPLICATION_JSON, "");
                })
                .baseUri("https://hub.dummyapis.com/statuscode/")
                .build();

        final var expectedRequestBody = """
                            {"inner":{"key":"key","value":"value"}}
                            """.trim();

        client.adaptExchange(new ExchangeAdapterClient.InnerBody("key", "value"));
        Assertions.assertEquals(expectedRequestBody, capturedBody.get(), "the transformed body must be the one sent");

        capturedBody.set(null);
        client.adaptExchangeForBodilessEntity(new ExchangeAdapterClient.InnerBody("key", "value"));
        Assertions.assertEquals(expectedRequestBody, capturedBody.get(), "the transformed body must be the one sent");

        capturedBody.set(null);
        client.adaptExchangeForBody(new ExchangeAdapterClient.InnerBody("key", "value"));
        Assertions.assertEquals(expectedRequestBody, capturedBody.get(), "the transformed body must be the one sent");

        capturedBody.set(null);
        client.adaptExchangeForEntity(new ExchangeAdapterClient.InnerBody("key", "value"));
        Assertions.assertEquals(expectedRequestBody, capturedBody.get(), "the transformed body must be the one sent");

    }

    public static class AddWrapperToRequest implements HttpRequestValuesTransformer {

        @Override
        public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        }

        @Override
        public HttpRequestValues transform(InvocationContext invocation, HttpRequestValues rv) {
            final var builder = HttpRequestValuesTransformer.valuesBuilder(rv);
            builder.setBodyValue(new Wrapper<>(rv.getBodyValue()));
            return builder.build();
        }

    }

    @Test
    public void valuesBuilderCopiesTheRequestValues() {
        final var builder = HttpRequestValues.builder()
                .setHttpMethod(HttpMethod.PUT)
                .setUriTemplate("/items/{id}")
                .setUriVariable("id", "42")
                .addHeader("X-Header", "a", "b")
                .addCookie("session", "s")
                .addAttribute("attribute", "value");
        builder.setBodyValue("body");
        final var copy = HttpRequestValuesTransformer.valuesBuilder(builder.build()).build();
        Assertions.assertEquals(HttpMethod.PUT, copy.getHttpMethod(), "the method must be copied");
        Assertions.assertEquals("/items/{id}", copy.getUriTemplate(), "the uri template must be copied");
        Assertions.assertEquals(Map.of("id", "42"), copy.getUriVariables(), "the uri variables must be copied");
        Assertions.assertEquals(List.of("a", "b"), copy.getHeaders().get("X-Header"), "every header value must be copied");
        Assertions.assertEquals(List.of("s"), copy.getCookies().get("session"), "the cookies must be copied");
        Assertions.assertEquals("value", copy.getAttributes().get("attribute"), "the attributes must be copied");
        Assertions.assertEquals("body", copy.getBodyValue(), "the body value must be copied");
    }

    private static class CapturingExchangeAdapter implements HttpExchangeAdapter {

        private HttpRequestValues values;

        @Override
        public boolean supportsRequestAttributes() {
            return true;
        }

        @Override
        public void exchange(HttpRequestValues requestValues) {
            this.values = requestValues;
        }

        @Override
        public HttpHeaders exchangeForHeaders(HttpRequestValues requestValues) {
            this.values = requestValues;
            return new HttpHeaders();
        }

        @Override
        public <T> T exchangeForBody(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
            this.values = requestValues;
            return null;
        }

        @Override
        public ResponseEntity<Void> exchangeForBodilessEntity(HttpRequestValues requestValues) {
            this.values = requestValues;
            return ResponseEntity.ok().build();
        }

        @Override
        public <T> ResponseEntity<T> exchangeForEntity(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
            this.values = requestValues;
            return ResponseEntity.ok().build();
        }
    }

    private static HttpRequestValuesTransformer appendingHeader(String value, List<String> preprocessed) {
        return new HttpRequestValuesTransformer() {
            @Override
            public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
                preprocessed.add(value);
            }

            @Override
            public HttpRequestValues transform(InvocationContext invocation, HttpRequestValues values) {
                return HttpRequestValuesTransformer.valuesBuilder(values).addHeader("X-Trace", value).build();
            }
        };
    }

    @Test
    public void theChainAppliesTheTransformersInOrder() {
        final var inner = new CapturingExchangeAdapter();
        final var preprocessed = new ArrayList<String>();
        final var chain = new UpstreamHttpExchangeAdapter.Chain(inner, List.of(appendingHeader("first", preprocessed), appendingHeader("second", preprocessed)));
        chain.preprocess(ExchangeAdapterClient.class, new Expressions(null, null), Map.of());
        chain.exchange(null, HttpRequestValues.builder().setHttpMethod(HttpMethod.GET).setUriTemplate("/").build());
        Assertions.assertEquals(List.of("first", "second"), preprocessed, "every transformer must be preprocessed, in order");
        Assertions.assertEquals(List.of("first", "second"), inner.values.getHeaders().get("X-Trace"), "the transformers must be applied in order before the exchange");
    }
}
