package net.optionfactory.spring.upstream.rest;

import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.log.UpstreamLogging;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.web.client.HttpClientErrorException;

@Ignore
public class RestExampleTest {

    @Test
    public void canCallDummyApi() {
        final var client = UpstreamBuilder
                .create(RestTestClient.class)
                .intercept(new UpstreamLogging.Interceptor())
                .restClient(r -> r.baseUrl("https://hub.dummyapis.com/statuscode/"))
                .build();
        final var response = client.ok("asd");
    }

    @Test(expected = HttpClientErrorException.BadRequest.class)
    public void badRequestYieldsException() {
        final var client = UpstreamBuilder
                .create(RestTestClient.class)
                .intercept(new UpstreamLogging.Interceptor())
                .restClient(r -> r.baseUrl("https://hub.dummyapis.com/statuscode/"))
                .build();
        client.error("asd");
    }

}
