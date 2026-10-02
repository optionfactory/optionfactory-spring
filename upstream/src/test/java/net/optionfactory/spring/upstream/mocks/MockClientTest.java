package net.optionfactory.spring.upstream.mocks;

import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

public class MockClientTest {

    private final MockClient client = UpstreamBuilder.create(MockClient.class)
            .requestFactoryMock(c -> {
            })
            .json(JsonMapper.builder().build())
            .baseUri("http://example.com")
            .build();

    @Test
    public void canUseMockResources() {
        final var got = client.add("a", "b");
        Assertions.assertEquals(Map.of("a", "b"), got.getBody(), "the body must come from the resource named by the evaluated template");
    }

    @Test
    public void canUseMockResponseStatus() {
        final var got = client.add("a", "b");
        Assertions.assertEquals(HttpStatus.CREATED.value(), got.getStatusCode().value(), "the status must be the one of the annotation");
    }

    @Test
    public void canUseMockContentType() {
        final var got = client.add("a", "b");
        Assertions.assertEquals(MediaType.parseMediaType("application/json;charset=utf-8"), got.getHeaders().getContentType(), "the Content-Type must default to the interface DefaultContentType");
    }

}
