package net.optionfactory.spring.upstream.buffering;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Cleaner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

/// A response whose body, once obtained, belongs to whoever obtained it: closing the response does not
/// close a delivered body, the caller's stream does.
///
/// This lets a client method return a stream over a pooled connection even though `RestClient`
/// closes the response as soon as the converter returns. When nobody asked for the body (no matching
/// converter, conversion failure, discarded response) closing the response releases the connection.
/// A body obtained and then abandoned without being closed, as spring's empty body probe does on the
/// paths that then fail, releases the connection once it becomes unreachable, instead of holding it
/// until the pool evicts it: closing the inner response is idempotent, so this last resort is
/// safe.
///
/// Not thread-safe.
public class StreamingUpstreamHttpResponse implements ClientHttpResponse {

    private static final Cleaner CLEANER = Cleaner.create();

    private final ClientHttpResponse inner;
    private boolean bodyDelivered = false;

    /// @param inner the response to stream
    public StreamingUpstreamHttpResponse(ClientHttpResponse inner) {
        this.inner = inner;
    }

    /// @return the status of the inner response
    /// @throws IOException when the status cannot be read
    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return this.inner.getStatusCode();
    }

    /// @return the status text of the inner response
    /// @throws IOException when the status cannot be read
    @Override
    public String getStatusText() throws IOException {
        return this.inner.getStatusText();
    }

    /// @return the headers of the inner response
    @Override
    public HttpHeaders getHeaders() {
        return this.inner.getHeaders();
    }

    /// @return the body, whose closing closes the inner response; every call wraps the inner body
    /// again
    /// @throws IOException when the body cannot be obtained
    @Override
    public InputStream getBody() throws IOException {
        this.bodyDelivered = true;
        final var body = new HttpInputMessageInputStream(inner);
        CLEANER.register(body, inner::close);
        return body;
    }

    /// Closes the inner response, unless the body was delivered: then the body owner closes it.
    @Override
    public void close() {
        if (!this.bodyDelivered) {
            this.inner.close();
        }
    }

}
