package net.optionfactory.spring.upstream.buffering;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.service.annotation.GetExchange;
import tools.jackson.databind.json.JsonMapper;

public class StreamingJsonClientTest {

    @Upstream("streaming-client")
    @Upstream.Logging
    public interface StreamingClient {

        @GetExchange("/1")
        @Upstream.Endpoint("endpoint-1")
        @Upstream.Mock(value = "streaming.json", headers = "Content-Type: application/json")
        Stream<Bean> fetchStream();

        @GetExchange("/2")
        @Upstream.Endpoint("endpoint-2")
        @Upstream.Mock(value = "streaming.json", headers = "Content-Type: application/json")
        ResponseEntity<Stream<Bean>> fetchStreamWithResponseEntity();

        @GetExchange("/3")
        @Upstream.Endpoint("endpoint-3")
        @Upstream.Mock(value = "streaming.jsonl", headers = "Content-Type: application/jsonl")
        Stream<Bean> fetchStreamFromJsonl();
    }

    public record Bean(String key, String value) {

    }

    private final StreamingClient client = UpstreamBuilder.create(StreamingClient.class)
            .requestFactoryMock(c -> {
            })
            .json(new JsonMapper())
            .baseUri("http://example.com")
            .build();

    @Test
    public void canReadUnbufferedStreamWhenMappingToAStream() throws IOException {
        try (final var stream = client.fetchStream()) {
            Assertions.assertEquals(List.of(new Bean("k1", "v1"), new Bean("k2", "v2")), stream.toList());
        }
    }

    @Test
    public void canReadUnbufferedStreamWhenMappingToAResponseEntityWithStream() throws IOException {
        final var got = client.fetchStreamWithResponseEntity();
        try (final var stream = got.getBody()) {
            Assertions.assertEquals(List.of(new Bean("k1", "v1"), new Bean("k2", "v2")), stream.toList());
        }
    }

    @Test
    public void canReadUnbufferedStreamWhenMappingToAStreamFromJsonl() throws IOException {
        try (final var stream = client.fetchStreamFromJsonl()) {
            Assertions.assertEquals(List.of(new Bean("k1", "v1"), new Bean("k2", "v2")), stream.toList());
        }
    }

}
