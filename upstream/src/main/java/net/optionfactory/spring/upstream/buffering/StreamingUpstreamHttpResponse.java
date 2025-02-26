package net.optionfactory.spring.upstream.buffering;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.StreamUtils;

public class StreamingUpstreamHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse inner;

    @Nullable
    private byte[] body;

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
        return new HttpInputMessageInputStream(inner);
    }

    @Override
    public void close() {

    }

}
