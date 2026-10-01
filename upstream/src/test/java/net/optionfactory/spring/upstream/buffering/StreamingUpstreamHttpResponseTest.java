package net.optionfactory.spring.upstream.buffering;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

public class StreamingUpstreamHttpResponseTest {

    private static class RecordingResponse implements ClientHttpResponse {

        public boolean closed = false;
        public int bodyServed = 0;

        @Override
        public HttpStatusCode getStatusCode() {
            return HttpStatus.OK;
        }

        @Override
        public String getStatusText() {
            return "OK";
        }

        @Override
        public HttpHeaders getHeaders() {
            return new HttpHeaders();
        }

        @Override
        public ByteArrayInputStream getBody() {
            bodyServed++;
            return new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            closed = true;
        }

    }

    @Test
    public void closeWithoutBodyReleasesTheConnection() {
        final var inner = new RecordingResponse();
        try (final var response = new StreamingUpstreamHttpResponse(inner)) {
            Assertions.assertFalse(inner.closed);
        }
        Assertions.assertTrue(inner.closed, "an unread streaming response must release its connection on close");
    }

    @Test
    public void closeAfterBodyDeliveryLeavesTheStreamToItsConsumer() throws IOException {
        final var inner = new RecordingResponse();
        final var response = new StreamingUpstreamHttpResponse(inner);
        final var body = response.getBody();
        response.close();
        Assertions.assertFalse(inner.closed, "closing after delivering the body must not kill the caller's stream");
        try (body) {
            Assertions.assertEquals("content", new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }
        Assertions.assertTrue(inner.closed, "closing the delivered stream must release the connection");
    }

    @Test
    public void bodyCanBeProbedAndThenRead() throws IOException {
        // Spring's IntrospectingClientHttpResponse.hasEmptyMessageBody obtains the body before
        // the chosen converter does: repeated getBody() calls are part of the normal flow
        final var inner = new RecordingResponse();
        final var response = new StreamingUpstreamHttpResponse(inner);
        response.getBody().close();
        response.close();
        Assertions.assertTrue(inner.closed);
    }

    @Test
    public void abandonedBodyReleasesTheConnectionOnCollection() throws Exception {
        final var inner = new RecordingResponse();
        final var response = new StreamingUpstreamHttpResponse(inner);
        response.getBody(); // obtained and abandoned without closing, as Spring's body probe does on failing paths
        response.close();
        Assertions.assertFalse(inner.closed, "the delivered body still owns the connection");
        for (int i = 0; i < 100 && !inner.closed; i++) {
            System.gc();
            Thread.sleep(20);
        }
        Assertions.assertTrue(inner.closed, "an abandoned body must release its connection once unreachable");
    }
}
