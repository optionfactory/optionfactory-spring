package net.optionfactory.spring.upstream.buffering;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

public class BufferingUpstreamHttpRequestTest {

    private static MockClientHttpRequest innerRespondingWith(String body) {
        final var inner = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://example.com/resource"));
        inner.setResponse(new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK));
        return inner;
    }

    private static String read(java.io.InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    public static class StreamingInner extends MockClientHttpRequest implements StreamingHttpOutputMessage {

        public Body body;

        public StreamingInner() {
            super(HttpMethod.POST, URI.create("http://example.com/resource"));
            setResponse(new MockClientHttpResponse(new byte[0], HttpStatus.OK));
        }

        @Override
        public void setBody(Body body) {
            this.body = body;
        }
    }

    @Test
    public void bodyIsWrittenToTheInnerRequestOnlyOnExecution() throws IOException {
        final var inner = innerRespondingWith("");
        final var request = new BufferingUpstreamHttpRequest(inner, Buffering.BUFFERED);
        request.getBody().write("payload".getBytes(StandardCharsets.UTF_8));
        Assertions.assertEquals("", inner.getBodyAsString(), "nothing must reach the inner request before execution");
        request.execute();
        Assertions.assertEquals("payload", inner.getBodyAsString(), "the buffered body must be written on execution");
    }

    @Test
    public void contentLengthIsComputedFromTheBufferedBody() throws IOException {
        final var inner = innerRespondingWith("");
        final var request = new BufferingUpstreamHttpRequest(inner, Buffering.BUFFERED);
        request.getBody().write("payload".getBytes(StandardCharsets.UTF_8));
        request.execute();
        Assertions.assertEquals(7, inner.getHeaders().getContentLength(), "a missing Content-Length must be set to the buffered body size");
    }

    @Test
    public void explicitContentLengthIsKept() throws IOException {
        final var inner = innerRespondingWith("");
        final var request = new BufferingUpstreamHttpRequest(inner, Buffering.BUFFERED);
        request.getHeaders().setContentLength(42);
        request.getBody().write("payload".getBytes(StandardCharsets.UTF_8));
        request.execute();
        Assertions.assertEquals(42, inner.getHeaders().getContentLength(), "an explicit Content-Length must not be overwritten");
    }

    @Test
    public void streamingInnerRequestsReceiveARepeatableBody() throws IOException {
        final var inner = new StreamingInner();
        final var request = new BufferingUpstreamHttpRequest(inner, Buffering.BUFFERED);
        request.getBody().write("payload".getBytes(StandardCharsets.UTF_8));
        request.execute();
        Assertions.assertTrue(inner.body.repeatable(), "the body handed to a streaming request must be repeatable");
        final var first = new ByteArrayOutputStream();
        inner.body.writeTo(first);
        final var second = new ByteArrayOutputStream();
        inner.body.writeTo(second);
        Assertions.assertEquals("payload", first.toString(StandardCharsets.UTF_8), "the streaming body must carry the buffered payload");
        Assertions.assertEquals("payload", second.toString(StandardCharsets.UTF_8), "the streaming body must be writable more than once");
    }

    @Test
    public void emptyBodyIsNotHandedToStreamingInnerRequests() throws IOException {
        final var inner = new StreamingInner();
        new BufferingUpstreamHttpRequest(inner, Buffering.BUFFERED).execute();
        Assertions.assertNull(inner.body, "an empty body must not be set on the inner request");
        Assertions.assertEquals(0, inner.getHeaders().getContentLength(), "an empty body must be declared with a zero Content-Length");
    }

    @Test
    public void requestCannotBeExecutedTwice() throws IOException {
        final var request = new BufferingUpstreamHttpRequest(innerRespondingWith(""), Buffering.BUFFERED);
        request.execute();
        Assertions.assertThrows(IllegalStateException.class, request::execute, "a second execution must be rejected");
        Assertions.assertThrows(IllegalStateException.class, request::getBody, "the body must not be writable after execution");
    }

    @Test
    public void headersBecomeReadOnlyAfterExecution() throws IOException {
        final var request = new BufferingUpstreamHttpRequest(innerRespondingWith(""), Buffering.BUFFERED);
        request.getHeaders().set("X-Before", "value");
        request.execute();
        Assertions.assertEquals("value", request.getHeaders().getFirst("X-Before"), "headers set before execution must stay readable");
        Assertions.assertThrows(UnsupportedOperationException.class, () -> request.getHeaders().set("X-After", "value"), "headers must not be modifiable after execution");
    }

    @Test
    public void bufferedResponseBodyCanBeReadMoreThanOnce() throws IOException {
        final var response = new BufferingUpstreamHttpRequest(innerRespondingWith("content"), Buffering.BUFFERED).execute();
        Assertions.assertInstanceOf(BufferingUpstreamHttpResponse.class, response, "a BUFFERED request must yield a buffering response");
        Assertions.assertEquals("content", read(response.getBody()), "the first read must see the whole body");
        Assertions.assertEquals("content", read(response.getBody()), "the body must be readable again from the buffer");
    }

    @Test
    public void unbufferedResponseIsTheInnerOne() throws IOException {
        final var inner = innerRespondingWith("content");
        final var response = new BufferingUpstreamHttpRequest(inner, Buffering.UNBUFFERED).execute();
        Assertions.assertSame(inner.execute(), response, "an UNBUFFERED request must yield the inner response untouched");
    }

    @Test
    public void streamingResponseIsWrapped() throws IOException {
        final var response = new BufferingUpstreamHttpRequest(innerRespondingWith("content"), Buffering.UNBUFFERED_STREAMING).execute();
        Assertions.assertInstanceOf(StreamingUpstreamHttpResponse.class, response, "an UNBUFFERED_STREAMING request must yield a streaming response");
    }

    @Test
    public void methodUriAndAttributesComeFromTheInnerRequest() {
        final var inner = innerRespondingWith("");
        inner.getAttributes().put("k", "v");
        inner.getHeaders().set("X-Inner", "value");
        final var request = new BufferingUpstreamHttpRequest(inner, Buffering.BUFFERED);
        Assertions.assertEquals(HttpMethod.POST, request.getMethod(), "the method must be the inner request's");
        Assertions.assertEquals(URI.create("http://example.com/resource"), request.getURI(), "the uri must be the inner request's");
        Assertions.assertEquals("v", request.getAttributes().get("k"), "the attributes must be the inner request's");
        Assertions.assertFalse(request.getHeaders().containsHeader("X-Inner"), "headers are the wrapper's own, copied onto the inner request on execution");
    }
}
