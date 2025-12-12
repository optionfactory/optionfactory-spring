package net.optionfactory.spring.upstream.scopes;

import java.lang.reflect.Method;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
                .restClient(r -> r.baseUrl("https://hub.dummyapis.com/statuscode/"))
                .build();

        final var expectedRequestBody = """
                            {"inner":{"key":"key","value":"value"}}
                            """.trim();

        client.adaptExchange(new ExchangeAdapterClient.InnerBody("key", "value"));
        Assertions.assertEquals(expectedRequestBody, capturedBody.get());

        capturedBody.set(null);
        client.adaptExchangeForBodilessEntity(new ExchangeAdapterClient.InnerBody("key", "value"));
        Assertions.assertEquals(expectedRequestBody, capturedBody.get());

        capturedBody.set(null);
        client.adaptExchangeForBody(new ExchangeAdapterClient.InnerBody("key", "value"));
        Assertions.assertEquals(expectedRequestBody, capturedBody.get());

        capturedBody.set(null);
        client.adaptExchangeForEntity(new ExchangeAdapterClient.InnerBody("key", "value"));
        Assertions.assertEquals(expectedRequestBody, capturedBody.get());

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
}
