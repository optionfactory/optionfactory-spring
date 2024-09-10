package net.optionfactory.spring.upstream.mocks.rendering;

import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.Assert;
import org.junit.Test;

public class ThymeleafRendererTest {

    @Test
    public void canRenderMockResourcesWithThymeleaf() {

        final var client = UpstreamBuilder.create(ThymeleafClient.class)
                .requestFactoryMock(c -> {
                    c.thymeleaf(null);
                })
                .restClient(r -> r.baseUrl("http://example.com"))
                .build();

        Map<String, String> got = client.testEndpoint("passed");

        Assert.assertEquals(Map.of("key", "passed"), got);
    }
}
