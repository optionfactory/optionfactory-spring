package net.optionfactory.spring.upstream.mocks.rendering;

import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class ThymeleafRendererTest {

    @Test
    public void canRenderMockResourcesWithThymeleaf() {

        final var client = UpstreamBuilder.create(ThymeleafClient.class)
                .requestFactoryMock(c -> c.thymeleaf())
                .json(JsonMapper.builder().build())
                .baseUri("http://example.com")
                .build();

        Map<String, String> got = client.testEndpoint("passed");

        Assertions.assertEquals(Map.of("key", "passed"), got);
    }
}
