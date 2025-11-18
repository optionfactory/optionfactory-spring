package net.optionfactory.spring.upstream.buffering;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

public class BufferingUpstreamHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse inner;

    @Nullable
    private byte[] body;

    public BufferingUpstreamHttpResponse(ClientHttpResponse inner) {
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
        if (this.body == null) {
            this.body = StreamUtils.copyToByteArray(this.inner.getBody());
        }
        return new ByteArrayInputStream(this.body);
    }

    @Override
    public void close() {
        this.inner.close();
    }

}
