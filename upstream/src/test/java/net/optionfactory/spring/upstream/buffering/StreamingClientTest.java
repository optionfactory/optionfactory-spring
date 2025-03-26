package net.optionfactory.spring.upstream.buffering;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.service.annotation.GetExchange;

public class StreamingClientTest {

    @Upstream("streaming-client")
    @Upstream.Logging
    public interface StreamingClient {

        @GetExchange("/")
        @Upstream.Endpoint("endpoint")
        @Upstream.Mock("streaming.txt")
        ResponseEntity<InputStream> fetchWithResponseEntity();

        @GetExchange("/")
        @Upstream.Endpoint("endpoint")
        @Upstream.Mock("streaming.txt")
        InputStream fetch();

        @GetExchange("/")
        @Upstream.Endpoint("endpoint")
        @Upstream.Mock(value = "streaming.json", headers = "Content-Type: application/json")
        Stream<String> fetchStream();

        @GetExchange("/")
        @Upstream.Endpoint("endpoint")
        @Upstream.Mock(value = "streaming.json", headers = "Content-Type: application/json")
        ResponseEntity<Stream<String>> fetchStreamWithResponseEntity();

        @GetExchange("/")
        @Upstream.Endpoint("endpoint")
        @Upstream.Mock(value = "streaming.jsonl", headers = "Content-Type: application/jsonl")
        Stream<String> fetchStreamFromJsonl();
    }

    private final StreamingClient client = UpstreamBuilder.create(StreamingClient.class)
            .requestFactoryMock(c -> {
            })
            .json(new ObjectMapper())
            .restClient(r -> {
                r.baseUrl("http://example.com");
            })
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

    @Test
    public void canReadUnbufferedStreamWhenMappingToAStream() throws IOException {
        try (final var stream = client.fetchStream()) {
            Assert.assertEquals(List.of("a", "b", "c"), stream.toList());
        }
    }

    @Test
    public void canReadUnbufferedStreamWhenMappingToAResponseEntityWithStream() throws IOException {
        final var got = client.fetchStreamWithResponseEntity();
        try (final var stream = got.getBody()) {
            Assert.assertEquals(List.of("a", "b", "c"), stream.toList());
        }
    }

    @Test
    public void canReadUnbufferedStreamWhenMappingToAStreamFromJsonl() throws IOException {
        try (final var stream = client.fetchStreamFromJsonl()) {
            Assert.assertEquals(List.of("a", "b", "c"), stream.toList());
        }
    }

}
