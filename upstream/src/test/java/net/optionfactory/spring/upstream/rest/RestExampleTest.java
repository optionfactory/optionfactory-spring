package net.optionfactory.spring.upstream.rest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.log.UpstreamLoggingInterceptor;
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
                .requestFactory((UpstreamHttpInterceptor.InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                    HttpHeaders h = new HttpHeaders();
                    h.setContentType(MediaType.APPLICATION_JSON);
                    final var content = """
                        {"a": "b"}
                    """;
                    return new MockClientHttpResponse(HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), h, new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
                })
                .intercept(new UpstreamLoggingInterceptor())
                .restClient(r -> r.baseUrl("https://hub.dummyapis.com/statuscode/"))
                .build();
        final var response = client.ok("asd");
        Assert.assertEquals("b", response.get("a"));
    }

    @Test(expected = HttpClientErrorException.BadRequest.class)
    public void badRequestYieldsException() {
        final var client = UpstreamBuilder
                .create(RestTestClient.class)
                .requestFactory((UpstreamHttpInterceptor.InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                    HttpHeaders h = new HttpHeaders();
                    h.setContentType(MediaType.APPLICATION_JSON);
                    final var content = "";
                    return new MockClientHttpResponse(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase(), h, new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
                })
                .intercept(new UpstreamLoggingInterceptor())
                .restClient(r -> r.baseUrl("https://hub.dummyapis.com/statuscode/"))
                .build();
        client.error("asd");
    }

}
