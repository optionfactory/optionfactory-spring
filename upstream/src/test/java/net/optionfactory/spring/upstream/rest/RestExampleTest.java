package net.optionfactory.spring.upstream.rest;

import io.micrometer.observation.ObservationRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

public class RestExampleTest {

    @Test
    public void canCallDummyApi() {
        final var client = UpstreamBuilder
                .create(RestTestClient.class)
                .requestFactory((InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                    HttpHeaders h = new HttpHeaders();
                    h.setContentType(MediaType.APPLICATION_JSON);
                    final var content = """
                        {"a": "b"}
                    """;
                    return new MockClientHttpResponse(HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), h, new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
                })
                .restClient(r -> r.baseUrl("https://hub.dummyapis.com/statuscode/"))
                .build(ObservationRegistry.NOOP, e -> {});
        final var response = client.ok("asd");
        Assert.assertEquals("b", response.get("a"));
    }

    @Test(expected = HttpClientErrorException.BadRequest.class)
    public void badRequestYieldsException() {
        final var client = UpstreamBuilder
                .create(RestTestClient.class)
                .requestFactory((InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                    HttpHeaders h = new HttpHeaders();
                    h.setContentType(MediaType.APPLICATION_JSON);
                    final var content = "";
                    return new MockClientHttpResponse(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase(), h, new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
                })
                .restClient(r -> r.baseUrl("https://hub.dummyapis.com/statuscode/"))
                .build(ObservationRegistry.NOOP, e -> {});
        client.error("asd");
    }

}
