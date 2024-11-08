package net.optionfactory.spring.upstream.buffering;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.service.annotation.GetExchange;

public class StreamingClientTest {

    @Upstream("streaming-client")
    @Upstream.Logging
    public interface StreamingClient {

        @GetExchange("/")
        @Upstream.Endpoint("endpoint")
        @Upstream.Mock("streaming.txt")
        ResponseEntity<InputStreamResource> fetchWithResponseEntity();

        @GetExchange("/")
        @Upstream.Endpoint("endpoint")
        @Upstream.Mock("streaming.txt")
        ResponseEntity<InputStreamResource> fetch();

    }

    private final StreamingClient client = UpstreamBuilder.create(StreamingClient.class)
            .requestFactoryMock(c -> {
            })
            .json(new ObjectMapper())
            .restClient(r -> r.baseUrl("http://example.com"))
            .build();

    @Test
    public void canReadUnbufferedStreamWhenMappingToAnInputStreamResource() throws IOException {
        final var got = client.fetch();
        try (final var is = got.getBody().getInputStream()) {
            Assert.assertEquals("content", new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void canReadUnbufferedStreamWhenMappingToAResponseEntityWithInputStreamResource() throws IOException {
        final var got = client.fetchWithResponseEntity();
        try (final var is = got.getBody().getInputStream()) {
            Assert.assertEquals("content", new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test(expected = IllegalStateException.class)
    public void readingTwiceFromInputStreamResourceThrows() throws IOException {
        final var got = client.fetch();
        try (final var is = got.getBody().getInputStream()) {
        }
        try (final var is = got.getBody().getInputStream()) {
        }
    }
}
