package net.optionfactory.spring.upstream.scopes;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;

public class InterceptorChainAdapterTest {

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");
    private static final InvocationContext INVOCATION = new InvocationContext(null, null, null, null, new Object[0], "boot", 1, null, Buffering.BUFFERED);

    private final List<String> trace = new ArrayList<>();
    private final AtomicReference<HttpRequest> executed = new AtomicReference<>();
    private final AtomicReference<RequestContext> recordedRequest = new AtomicReference<>();
    private final AtomicReference<ResponseContext> recordedResponse = new AtomicReference<>();

    private final ClientHttpRequestExecution execution = (request, body) -> {
        executed.set(request);
        trace.add("execute " + request.getURI() + " " + new String(body, StandardCharsets.UTF_8));
        return new MockClientHttpResponse(HttpStatus.ACCEPTED, "Accepted", new HttpHeaders(), new ByteArrayResource("response".getBytes(StandardCharsets.UTF_8)));
    };

    private UpstreamHttpInterceptor tracing(String name) {
        return (invocation, request, chain) -> {
            trace.add(name + " " + (invocation == INVOCATION));
            return chain.execute(invocation, request);
        };
    }

    private InterceptorChainAdapter adapter(List<UpstreamHttpInterceptor> interceptors) {
        return new InterceptorChainAdapter(interceptors, () -> INVOCATION, recordedRequest::set, recordedResponse::set, InstantSource.fixed(NOW));
    }

    private static MockClientHttpRequest request() {
        return new MockClientHttpRequest(HttpMethod.POST, URI.create("http://example.com/original"));
    }

    @Test
    public void interceptorsRunInOrderBeforeTheExecution() throws IOException {
        adapter(List.of(tracing("first"), tracing("second"))).intercept(request(), "body".getBytes(StandardCharsets.UTF_8), execution);
        Assertions.assertEquals(List.of("first true", "second true", "execute http://example.com/original body"), trace, "interceptors must run in list order, with the current invocation, before the execution");
    }

    @Test
    public void theRequestReachingTheEndOfTheChainIsExecutedAndRecorded() throws IOException {
        final UpstreamHttpInterceptor rewriting = (invocation, request, chain) -> chain.execute(invocation, request.withUri(URI.create("http://example.com/rewritten")));
        adapter(List.of(rewriting)).intercept(request(), new byte[0], execution);
        Assertions.assertEquals(URI.create("http://example.com/rewritten"), executed.get().getURI(), "the request passed on by the interceptors must be the one executed");
        Assertions.assertEquals(URI.create("http://example.com/rewritten"), recordedRequest.get().uri(), "the request executed must be recorded");
        Assertions.assertEquals(NOW, recordedRequest.get().at(), "the request context must be timestamped by the clock");
    }

    @Test
    public void theResponseIsAdaptedAndRecorded() throws IOException {
        final var response = adapter(List.of()).intercept(request(), new byte[0], execution);
        final var adapted = Assertions.assertInstanceOf(ResponseAdapter.class, response, "the response must carry the contexts on as a ResponseAdapter");
        Assertions.assertSame(INVOCATION, adapted.invocation(), "the adapted response must carry the invocation");
        Assertions.assertSame(recordedResponse.get(), adapted.response(), "the response context returned must be the recorded one");
        Assertions.assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(), "the status must come from the response context");
        Assertions.assertEquals("response", new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8), "the body must come from the response context");
        Assertions.assertEquals(NOW, recordedResponse.get().at(), "the response context must be timestamped by the clock");
    }

    @Test
    public void anInterceptorCanAnswerWithoutExecuting() throws IOException {
        final var answer = new ResponseContext(NOW, HttpStatus.NO_CONTENT, "No Content", new HttpHeaders(), ResponseContext.BodySource.of(new byte[0]), false);
        final UpstreamHttpInterceptor answering = (invocation, request, chain) -> answer;
        final var response = adapter(List.of(answering)).intercept(request(), new byte[0], execution);
        Assertions.assertNull(executed.get(), "an interceptor that does not proceed must prevent the execution");
        Assertions.assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "the response must be the one the interceptor returned");
        Assertions.assertNull(recordedRequest.get(), "no request must be recorded when nothing is executed");
    }
}
