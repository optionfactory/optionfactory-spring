package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

public class MockClientHttpResponse implements ClientHttpResponse {

    private final HttpStatusCode statusCode;
    private final String statusText;
    private final HttpHeaders headers;
    private final InputStreamSource body;

    public MockClientHttpResponse(HttpStatusCode statusCode, String statusText, HttpHeaders headers, InputStreamSource body) {
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.headers = headers;
        this.body = body;
    }

    public static MockClientHttpResponse ok(MediaType mediaType, byte[] content) {
        final HttpHeaders h = new HttpHeaders();
        h.setContentType(mediaType);
        return new MockClientHttpResponse(HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), h, new ByteArrayResource(content));
    }

    public static MockClientHttpResponse okUtf8(MediaType mediaType, String content) {
        return ok(mediaType, content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return statusCode;
    }

    @Override
    public String getStatusText() throws IOException {
        return statusText;
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public InputStream getBody() throws IOException {
        return body.getInputStream();
    }

    @Override
    public void close() {
    }

}
