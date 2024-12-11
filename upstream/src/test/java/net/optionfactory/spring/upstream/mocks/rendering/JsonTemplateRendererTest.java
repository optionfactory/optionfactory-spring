package net.optionfactory.spring.upstream.mocks.rendering;

import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.Assert;
import org.junit.Test;

public class JsonTemplateRendererTest {

    @Test
    public void canRenderMockResourcesWithJsonTemplate() {

        final var client = UpstreamBuilder.create(JsonTemplateClient.class)
                .requestFactoryMock(c -> c.jsont())
                .restClient(r -> r.baseUrl("http://example.com"))
                .build();

        Map<String, String> got = client.testEndpoint("passed");

        Assert.assertEquals(Map.of("key", "passed"), got);
    }
}
