package net.optionfactory.spring.upstream.mocks.rendering;

import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ThymeleafRendererTest {

    @Test
    public void canRenderMockResourcesWithThymeleaf() {

        final var client = UpstreamBuilder.create(ThymeleafClient.class)
                .requestFactoryMock(c -> c.thymeleaf())
                .restClient(r -> r.baseUrl("http://example.com"))
                .build();

        Map<String, String> got = client.testEndpoint("passed");

        Assertions.assertEquals(Map.of("key", "passed"), got);
    }
}
