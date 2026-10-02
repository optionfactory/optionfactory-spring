package net.optionfactory.spring.upstream.buffering;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.StreamUtils;

/// A request whose body and headers are held in memory until it is executed, and whose response is
/// wrapped according to a [Buffering].
///
/// On execution the headers are copied onto the inner request, `Content-Length` is set to the body
/// size unless already set, and a non empty body is handed to the inner request: as a repeatable
/// body when it is a `StreamingHttpOutputMessage`, written to its output stream otherwise.
///
/// A request is executed once, and is not thread-safe.
public class BufferingUpstreamHttpRequest implements ClientHttpRequest {

    private final ClientHttpRequest inner;

    private final FastByteArrayOutputStream bufferedOutput = new FastByteArrayOutputStream(1024);
    private final HttpHeaders headers = new HttpHeaders();

    private boolean executed = false;

    @Nullable
    private HttpHeaders readOnlyHeaders;

    private final Buffering buffering;

    /// @param inner the request actually sent
    /// @param buffering how the response is wrapped
    public BufferingUpstreamHttpRequest(ClientHttpRequest inner, Buffering buffering) {
        this.inner = inner;
        this.buffering = buffering;
    }

    /// @return a [BufferingUpstreamHttpResponse] when `BUFFERED`, the inner response when `UNBUFFERED`,
    /// a [StreamingUpstreamHttpResponse] when `UNBUFFERED_STREAMING`
    /// @throws IOException when the inner request fails
    /// @throws IllegalStateException when the request was already executed
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

    /// @return the method of the inner request
    @Override
    public HttpMethod getMethod() {
        return inner.getMethod();
    }

    /// @return the uri of the inner request
    @Override
    public URI getURI() {
        return inner.getURI();
    }

    /// @return the attributes of the inner request
    @Override
    public Map<String, Object> getAttributes() {
        return inner.getAttributes();
    }

    /// @return the headers of this request, copied onto the inner one on execution; read-only once
    /// executed
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

    /// @return the in-memory buffer of the body
    /// @throws IllegalStateException when the request was already executed
    @Override
    public OutputStream getBody() throws IOException {
        Assert.state(!this.executed, "ClientHttpRequest already executed");
        return this.bufferedOutput;
    }

}
