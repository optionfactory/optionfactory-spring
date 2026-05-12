package net.optionfactory.spring.upstream.mocks.rendering;

import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class JsonTemplateRendererTest {

    @Test
    public void canRenderMockResourcesWithJsonTemplate() {

        final var client = UpstreamBuilder.create(JsonTemplateClient.class)
                .requestFactoryMock(c -> c.jsont())
                .json(JsonMapper.builder().build())
                .baseUri("http://example.com")
                .build();

        Map<String, String> got = client.testEndpoint("passed");

        Assertions.assertEquals(Map.of("key", "passed"), got);
    }
}
