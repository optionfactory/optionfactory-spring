package net.optionfactory.spring.upstream.buffering;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
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
        ResponseEntity<InputStream> fetch();

    }

    private final StreamingClient client = UpstreamBuilder.create(StreamingClient.class)
            .requestFactoryMock(c -> {
            })
            .json(new ObjectMapper())
            .restClient(r -> {
                r.messageConverters(List.of(new InputStreamHttpMessageConverter()));
                r.baseUrl("http://example.com");
            })
            .build();

    @Test
    public void canReadUnbufferedStreamWhenMappingToAnInputStreamResource() throws IOException {
        final var got = client.fetch();
        try (final var is = got.getBody()) {
            Assert.assertEquals("content", new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void canReadUnbufferedStreamWhenMappingToAResponseEntityWithInputStreamResource() throws IOException {
        final var got = client.fetchWithResponseEntity();
        try (final var is = got.getBody()) {
            Assert.assertEquals("content", new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }


}
