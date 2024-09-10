package net.optionfactory.spring.upstream.rest;

import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

public class RestExampleTest {

    @Test
    public void canCallDummyApi() {
        final var client = UpstreamBuilder
                .create(RestTestClient.class)
                .requestFactoryMock(c -> {
                    c.response(MediaType.APPLICATION_JSON, """
                        {"a": "b"}
                    """);
                })
                .restClient(r -> r.baseUrl("https://hub.dummyapis.com/statuscode/"))
                .build();
        final var response = client.ok("asd");
        Assert.assertEquals("b", response.get("a"));
    }

    @Test(expected = HttpClientErrorException.BadRequest.class)
    public void badRequestYieldsException() {
        final var client = UpstreamBuilder
                .create(RestTestClient.class)
                .requestFactoryMock(c -> {
                    c.response(HttpStatus.BAD_REQUEST, MediaType.APPLICATION_JSON, "");
                })
                .restClient(r -> r.baseUrl("https://hub.dummyapis.com/statuscode/"))
                .build();
        client.error("asd");
    }

}
