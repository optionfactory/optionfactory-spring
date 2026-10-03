package net.optionfactory.spring.upstream.mocks.redeclared;

import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

public class RedeclaringMockClientTest {

    @Test
    public void redeclaredEndpointsKeepTheMocksOfTheDeclarationTheyOverride() {
        final var client = UpstreamBuilder.create(RedeclaringMockClient.class)
                .requestFactoryMock(c -> {
                })
                .json(JsonMapper.builder().build())
                .baseUri("http://example.com")
                .build();
        final var got = client.add("a", "b");
        Assertions.assertEquals(Map.of("a", "b"), got.getBody(), "the mock resource must be resolved next to the interface whose declaration carries the annotation");
        Assertions.assertEquals(HttpStatus.CREATED.value(), got.getStatusCode().value(), "the status must be the one of the inherited annotation");
    }
}
