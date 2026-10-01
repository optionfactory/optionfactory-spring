package net.optionfactory.spring.upstream.buffering;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Cleaner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

public class StreamingUpstreamHttpResponse implements ClientHttpResponse {

    private static final Cleaner CLEANER = Cleaner.create();

    private final ClientHttpResponse inner;
    private boolean bodyDelivered = false;

    public StreamingUpstreamHttpResponse(ClientHttpResponse inner) {
        this.inner = inner;
    }

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return this.inner.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
        return this.inner.getStatusText();
    }

    @Override
    public HttpHeaders getHeaders() {
        return this.inner.getHeaders();
    }

    @Override
    public InputStream getBody() throws IOException {
        this.bodyDelivered = true;
        final var body = new HttpInputMessageInputStream(inner);
        // abandoned bodies (Spring's hasEmptyMessageBody probe on paths that then fail, discarded
        // response bodies) would otherwise hold the pooled connection until HC5 pool eviction:
        // releasing the inner response is idempotent, so the cleaner is a safe last resort
        CLEANER.register(body, inner::close);
        return body;
    }

    // the body, when obtained, hands the pooled connection to its consumer: closing it here
    // (RestClient does, as soon as the converter returns) would kill the caller's stream.
    // When nobody asked for the body (no matching converter, conversion failure, discarded
    // response) the connection would otherwise never be released: close the inner response.
    @Override
    public void close() {
        if (!this.bodyDelivered) {
            this.inner.close();
        }
    }

}
