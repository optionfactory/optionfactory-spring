package net.optionfactory.spring.upstream;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

public class UpstreamQueryParamsTest {

    private static UpstreamQueryParamsClient clientExpecting(URI expected) {
        return UpstreamBuilder.create(UpstreamQueryParamsClient.class)
                .requestFactoryMock(c -> {
                    c.response(MediaType.APPLICATION_JSON, "{}");
                    c.responseFactory((InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                        Assertions.assertEquals(expected, uri);
                        final HttpHeaders h = new HttpHeaders();
                        h.setContentType(MediaType.APPLICATION_JSON);
                        return new MockClientHttpResponse(HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), h, new ByteArrayResource("{}".getBytes(StandardCharsets.UTF_8)));
                    });
                })
                .json(JsonMapper.builder().build())
                .baseUri("http://example.com")
                .build();
    }

    @Test
    public void annotatedQueryParamDoesNotDoubleEncodeExistingQuery() {
        clientExpecting(URI.create("http://example.com/endpoint?q=a%20b&extra=v"))
                .keepsExistingQueryParams("a b");
    }

    @Test
    public void annotatedQueryParamValueIsEncodedOnce() {
        clientExpecting(URI.create("http://example.com/endpoint?q=a%20b&extra=a%20b%26c%3Dd"))
                .encodesAddedQueryParams("a b");
    }
}
