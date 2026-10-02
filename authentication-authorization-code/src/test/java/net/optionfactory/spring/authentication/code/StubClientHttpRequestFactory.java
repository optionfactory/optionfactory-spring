package net.optionfactory.spring.authentication.code;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

/// Answers every request with the same json response, recording the requests: stands in for the
/// identity provider, so that no test touches the network.
class StubClientHttpRequestFactory implements ClientHttpRequestFactory {

    public final List<MockClientHttpRequest> requests = new ArrayList<>();
    private final HttpStatus status;
    private final String json;

    StubClientHttpRequestFactory(HttpStatus status, String json) {
        this.status = status;
        this.json = json;
    }

    @Override
    public ClientHttpRequest createRequest(java.net.URI uri, HttpMethod httpMethod) {
        final var response = new MockClientHttpResponse(json.getBytes(StandardCharsets.UTF_8), status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        final var request = new MockClientHttpRequest(httpMethod, uri);
        request.setResponse(response);
        requests.add(request);
        return request;
    }
}
