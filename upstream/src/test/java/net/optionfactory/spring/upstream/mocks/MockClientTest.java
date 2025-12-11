package net.optionfactory.spring.upstream.mocks;

import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class MockClientTest {

    private final MockClient client = UpstreamBuilder.create(MockClient.class)
            .requestFactoryMock(c -> {
            })
            .restClient(r -> r.baseUrl("http://example.com"))
            .build();

    @Test
    public void canUseMockResources() {
        final var got = client.add("a", "b");
        Assertions.assertEquals(Map.of("a", "b"), got.getBody());
    }

    @Test
    public void canUseMockResponseStatus() {
        final var got = client.add("a", "b");
        Assertions.assertEquals(HttpStatus.CREATED.value(), got.getStatusCode().value());
    }

    @Test
    public void canUseMockContentType() {
        final var got = client.add("a", "b");
        Assertions.assertEquals(MediaType.parseMediaType("application/json;charset=utf-8"), got.getHeaders().getContentType());
    }

}
