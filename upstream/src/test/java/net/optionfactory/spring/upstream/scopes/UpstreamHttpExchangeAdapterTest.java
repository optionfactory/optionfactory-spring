package net.optionfactory.spring.upstream.scopes;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import net.optionfactory.spring.upstream.scopes.ExchangeAdapterClient.Wrapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpRequestValues;

public class UpstreamHttpExchangeAdapterTest {

    @Test
    public void canAdaptRequestBody() {

        final var capturedBody = new AtomicReference<String>();

        final var client = UpstreamBuilder
                .create(ExchangeAdapterClient.class)
                .interceptor((invocation, request, execution) -> {
                    capturedBody.set(new String(request.body(), StandardCharsets.UTF_8));
                    return execution.execute(invocation, request);
                })
                .exchangeAdapter((inner) -> new AddWrapperToRequest(inner))
                .requestFactoryMock(c -> {
                    c.response(HttpStatus.OK, MediaType.APPLICATION_JSON, "");
                })
                .restClient(r -> r.baseUrl("https://hub.dummyapis.com/statuscode/"))
                .build();

        final var expectedRequestBody = """
                            {"inner":{"key":"key","value":"value"}}
                            """.trim();

        client.adaptExchange(new ExchangeAdapterClient.InnerBody("key", "value"));
        Assert.assertEquals(expectedRequestBody, capturedBody.get());

        capturedBody.set(null);
        client.adaptExchangeForBodilessEntity(new ExchangeAdapterClient.InnerBody("key", "value"));
        Assert.assertEquals(expectedRequestBody, capturedBody.get());

        capturedBody.set(null);
        client.adaptExchangeForBody(new ExchangeAdapterClient.InnerBody("key", "value"));
        Assert.assertEquals(expectedRequestBody, capturedBody.get());

        capturedBody.set(null);
        client.adaptExchangeForEntity(new ExchangeAdapterClient.InnerBody("key", "value"));
        Assert.assertEquals(expectedRequestBody, capturedBody.get());

    }

    public static class AddWrapperToRequest implements UpstreamHttpExchangeAdapter {

        private final HttpExchangeAdapter inner;

        public AddWrapperToRequest(HttpExchangeAdapter inner) {
            this.inner = inner;
        }

        @Override
        public boolean supportsRequestAttributes(InvocationContext invocation) {
            return inner.supportsRequestAttributes();
        }

        private HttpRequestValues adaptValues(HttpRequestValues rv) {
            final var builder = UpstreamHttpExchangeAdapter.valuesBuilder(rv);
            builder.setBodyValue(new Wrapper<>(rv.getBodyValue()));
            return builder.build();
        }

        @Override
        public void exchange(InvocationContext invocation, HttpRequestValues rv) {
            inner.exchange(adaptValues(rv));
        }

        @Override
        public HttpHeaders exchangeForHeaders(InvocationContext invocation, HttpRequestValues values) {
            return inner.exchangeForHeaders(adaptValues(values));
        }

        @Override
        public <T> T exchangeForBody(InvocationContext invocation, HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
            return inner.exchangeForBody(adaptValues(values), bodyType);
        }

        @Override
        public ResponseEntity<Void> exchangeForBodilessEntity(InvocationContext invocation, HttpRequestValues values) {
            return inner.exchangeForBodilessEntity(adaptValues(values));
        }

        @Override
        public <T> ResponseEntity<T> exchangeForEntity(InvocationContext invocation, HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
            return inner.exchangeForEntity(adaptValues(values), bodyType);
        }
    }
}
