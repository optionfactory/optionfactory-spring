package net.optionfactory.spring.upstream.buffering;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

/// A response whose body is read into memory on first access, and can then be read any number of
/// times: by the logs, the alerts, the error conditions and the mapping.
///
/// Not thread-safe.
public class BufferingUpstreamHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse inner;

    @Nullable
    private byte[] body;

    /// @param inner the response to buffer
    public BufferingUpstreamHttpResponse(ClientHttpResponse inner) {
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

    /// @return a new stream over the buffered body, read from the inner response on the first call
    /// @throws IOException when the inner body cannot be read
    @Override
    public InputStream getBody() throws IOException {
        if (this.body == null) {
            this.body = StreamUtils.copyToByteArray(this.inner.getBody());
        }
        return new ByteArrayInputStream(this.body);
    }

    /// Closes the inner response, releasing its connection; the buffered body stays readable.
    @Override
    public void close() {
        this.inner.close();
    }

}
