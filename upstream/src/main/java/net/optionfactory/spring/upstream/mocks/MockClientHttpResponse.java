package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
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
