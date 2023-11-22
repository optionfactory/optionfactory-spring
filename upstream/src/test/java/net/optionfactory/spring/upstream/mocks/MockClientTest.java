package net.optionfactory.spring.upstream.mocks;

import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.log.UpstreamLoggingInterceptor;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class MockClientTest {

    private final MockClient client = UpstreamBuilder.create(MockClient.class)
            .requestFactoryMockResources()
            .restClient(r -> r.baseUrl("http://example.com"))
            .intercept(new UpstreamLoggingInterceptor())
            .build();

    
    @Test
    public void canUseMockResources() {
        final var got = client.add("a", "b");
        Assert.assertEquals(Map.of("a", "b"), got.getBody());
    }

    @Test
    public void canUseMockResponseStatus() {
        final var got = client.add("a", "b");
        Assert.assertEquals(HttpStatus.CREATED.value(), got.getStatusCode().value());
    }

    @Test
    public void canUseMockContentType() {
        final var got = client.add("a", "b");
        Assert.assertEquals(MediaType.parseMediaType("application/json;charset=utf-8"), got.getHeaders().getContentType());
    }

}
