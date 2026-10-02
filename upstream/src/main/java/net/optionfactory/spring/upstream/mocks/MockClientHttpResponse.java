package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

/// A `ClientHttpResponse` with a fixed status, headers and body, as returned by mocked exchanges.
///
/// The body is opened anew from its `InputStreamSource` on every [#getBody()] call, so with a
/// re-readable source (a `ByteArrayResource`, a `ClassPathResource`) the same response can be read
/// more than once, and returned for more than one exchange.
///
/// ```java
/// final var headers = new HttpHeaders();
/// headers.setContentType(MediaType.APPLICATION_JSON);
/// return new MockClientHttpResponse(HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), headers, new ByteArrayResource("{}".getBytes(StandardCharsets.UTF_8)));
/// ```
public class MockClientHttpResponse implements ClientHttpResponse {

    private final HttpStatusCode statusCode;
    private final String statusText;
    private final HttpHeaders headers;
    private final InputStreamSource body;

    /// @param statusCode the response status
    /// @param statusText the reason phrase
    /// @param headers the response headers, exposed as they are (not copied)
    /// @param body the source of the body, opened on each [#getBody()] call
    public MockClientHttpResponse(HttpStatusCode statusCode, String statusText, HttpHeaders headers, InputStreamSource body) {
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.headers = headers;
        this.body = body;
    }

    /// @return the configured status
    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return statusCode;
    }

    /// @return the configured reason phrase
    @Override
    public String getStatusText() throws IOException {
        return statusText;
    }

    /// @return the configured headers instance
    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    /// @return a new stream from the body source
    /// @throws IOException when the source cannot be opened
    @Override
    public InputStream getBody() throws IOException {
        return body.getInputStream();
    }

    /// Does nothing: there is no connection to release.
    @Override
    public void close() {
    }

}
