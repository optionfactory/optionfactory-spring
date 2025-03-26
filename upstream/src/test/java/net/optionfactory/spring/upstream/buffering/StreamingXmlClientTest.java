package net.optionfactory.spring.upstream.buffering;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.service.annotation.GetExchange;

public class StreamingXmlClientTest {

    @Upstream("streaming-client")
    @Upstream.Logging
    public interface StreamingClient {

        @GetExchange("/")
        @Upstream.Endpoint("endpoint")
        @Upstream.Mock(value = "streaming.xml", headers = "Content-Type: application/xml")
        Stream<Bean> fetchStream();

    }

    public record Bean(String key, String value) {

    }

    private final StreamingClient client = UpstreamBuilder.create(StreamingClient.class)
            .requestFactoryMock(c -> {})
            .xml(new XmlMapper())
            .baseUri("http://example.com")
            .build();

    @Test
    public void canReadUnbufferedStreamWhenMappingToAStream() throws IOException {
        try (final var stream = client.fetchStream()) {
            Assert.assertEquals(List.of(new Bean("k1", "v1"), new Bean("k2", "v2")), stream.toList());
        }
    }

}
