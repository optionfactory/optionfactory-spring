package net.optionfactory.spring.upstream.buffering;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.service.annotation.GetExchange;
import tools.jackson.databind.json.JsonMapper;

public class StreamingClientTest {

    @Upstream("streaming-client")
    @Upstream.Logging
    public interface StreamingClient {

        @GetExchange("/1")
        @Upstream.Endpoint("endpoint-1")
        @Upstream.Mock("streaming.txt")
        ResponseEntity<InputStream> fetchWithResponseEntity();

        @GetExchange("/2")
        @Upstream.Endpoint("endpoint-2")
        @Upstream.Mock("streaming.txt")
        InputStream fetch();

    }

    private final StreamingClient client = UpstreamBuilder.create(StreamingClient.class)
            .requestFactoryMock(c -> {
            })
            .json(new JsonMapper())
            .baseUri("http://example.com")
            .build();

    @Test
    public void canReadUnbufferedStreamWhenMappingToAnInputStream() throws IOException {
        final var got = client.fetch();
        try (final var is = got) {
            Assert.assertEquals("content", new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void canReadUnbufferedStreamWhenMappingToAResponseEntityWithInputStream() throws IOException {
        final var got = client.fetchWithResponseEntity();
        try (final var is = got.getBody()) {
            Assert.assertEquals("content", new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
