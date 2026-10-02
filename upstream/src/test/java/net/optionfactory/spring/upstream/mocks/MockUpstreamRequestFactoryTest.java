package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

public class MockUpstreamRequestFactoryTest {

    private static class RecordingResponseFactory implements UpstreamHttpResponseFactory {

        private final AtomicReference<Class<?>> preprocessed = new AtomicReference<>();
        private final AtomicReference<String> seen = new AtomicReference<>();

        @Override
        public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
            preprocessed.set(k);
        }

        @Override
        public ClientHttpResponse create(InvocationContext invocation, URI uri, HttpMethod method, HttpHeaders headers) {
            seen.set(method + " " + uri + " " + headers.getFirst("X-Test"));
            return new MockClientHttpResponse(HttpStatus.OK, "OK", new HttpHeaders(), new ByteArrayResource(new byte[0]));
        }
    }

    @Test
    public void theResponseFactorySeesTheRequestAsConfiguredBeforeExecution() throws IOException {
        final var responses = new RecordingResponseFactory();
        final var request = new MockUpstreamRequestFactory(responses).createRequest(null, URI.create("http://example.com/a"), HttpMethod.POST);
        request.getHeaders().set("X-Test", "value");
        request.getBody().write("ignored".getBytes(StandardCharsets.UTF_8));
        request.execute();
        Assertions.assertEquals("POST http://example.com/a value", responses.seen.get(), "the response factory must receive the method, the uri and the headers set on the request");
    }

    @Test
    public void preprocessingIsDelegatedToTheResponseFactory() {
        final var responses = new RecordingResponseFactory();
        new MockUpstreamRequestFactory(responses).preprocess(String.class, new Expressions(null, null), Map.of());
        Assertions.assertEquals(String.class, responses.preprocessed.get(), "the response factory must preprocess the client");
    }
}
