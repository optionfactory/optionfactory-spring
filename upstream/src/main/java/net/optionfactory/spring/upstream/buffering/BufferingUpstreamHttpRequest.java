package net.optionfactory.spring.upstream.buffering;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.StreamUtils;

public class BufferingUpstreamHttpRequest implements ClientHttpRequest {

    private final ClientHttpRequest inner;

    private final FastByteArrayOutputStream bufferedOutput = new FastByteArrayOutputStream(1024);
    private final HttpHeaders headers = new HttpHeaders();

    private boolean executed = false;

    @Nullable
    private HttpHeaders readOnlyHeaders;

    private final Buffering buffering;

    public BufferingUpstreamHttpRequest(ClientHttpRequest inner, Buffering buffering) {
        this.inner = inner;
        this.buffering = buffering;
    }

    @Override
    public ClientHttpResponse execute() throws IOException {
        Assert.state(!this.executed, "ClientHttpRequest already executed");
        final var bytes = this.bufferedOutput.toByteArrayUnsafe();
        if (headers.getContentLength() < 0) {
            headers.setContentLength(bytes.length);
        }
        inner.getHeaders().putAll(headers);

        if (bytes.length > 0) {
            if (inner instanceof StreamingHttpOutputMessage streamingHttpOutputMessage) {
                streamingHttpOutputMessage.setBody(new StreamingHttpOutputMessage.Body() {
                    @Override
                    public void writeTo(OutputStream outputStream) throws IOException {
                        StreamUtils.copy(bytes, outputStream);
                    }

                    @Override
                    public boolean repeatable() {
                        return true;
                    }
                });
            } else {
                StreamUtils.copy(bytes, inner.getBody());
            }
        }

        final var response = inner.execute();
        this.bufferedOutput.reset();
        this.executed = true;
        return switch(buffering){
            case BUFFERED -> new BufferingUpstreamHttpResponse(response);
            case UNBUFFERED -> response;
            case UNBUFFERED_STREAMING -> new StreamingUpstreamHttpResponse(response);
        };
    }

    @Override
    public HttpMethod getMethod() {
        return inner.getMethod();
    }

    @Override
    public URI getURI() {
        return inner.getURI();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return inner.getAttributes();
    }

    @Override
    public HttpHeaders getHeaders() {
        if (this.readOnlyHeaders != null) {
            return this.readOnlyHeaders;
        } else if (this.executed) {
            this.readOnlyHeaders = HttpHeaders.readOnlyHttpHeaders(this.headers);
            return this.readOnlyHeaders;
        } else {
            return this.headers;
        }
    }

    @Override
    public OutputStream getBody() throws IOException {
        Assert.state(!this.executed, "ClientHttpRequest already executed");
        return this.bufferedOutput;
    }

}
