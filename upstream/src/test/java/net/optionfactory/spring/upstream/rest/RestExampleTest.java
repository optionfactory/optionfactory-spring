package net.optionfactory.spring.upstream.rest;

import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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
        Assertions.assertEquals("b", response.get("a"));
    }

    @Test
    public void badRequestYieldsException() {
        final var client = UpstreamBuilder
                .create(RestTestClient.class)
                .requestFactoryMock(c -> {
                    c.response(HttpStatus.BAD_REQUEST, MediaType.APPLICATION_JSON, "");
                })
                .restClient(r -> r.baseUrl("https://hub.dummyapis.com/statuscode/"))
                .build();
        Assertions.assertThrows(HttpClientErrorException.BadRequest.class, () -> {
            client.error("asd");
        });
    }

}
