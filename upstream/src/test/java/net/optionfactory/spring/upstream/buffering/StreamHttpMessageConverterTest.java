package net.optionfactory.spring.upstream.buffering;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.mock.http.MockHttpInputMessage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.xml.XmlMapper;

public class StreamHttpMessageConverterTest {

    public record Bean(String key, String value) {

    }

    private static final Type STREAM_OF_BEANS = new ParameterizedTypeReference<Stream<Bean>>() {
    }.getType();

    private static final Type LIST_OF_BEANS = new ParameterizedTypeReference<List<Bean>>() {
    }.getType();

    private static class TrackingInputStream extends ByteArrayInputStream {

        public boolean closed;

        public TrackingInputStream(String content) {
            super(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    @Test
    public void jsonConverterReadsStreamsFromJsonAndJsonLines() {
        final var converter = StreamHttpMessageConverter.forJson(new JsonMapper());
        Assertions.assertTrue(converter.canRead(STREAM_OF_BEANS, null, MediaType.APPLICATION_JSON), "application/json must be readable");
        Assertions.assertTrue(converter.canRead(STREAM_OF_BEANS, null, MediaType.valueOf("application/jsonl")), "json lines must be readable");
        Assertions.assertTrue(converter.canRead(STREAM_OF_BEANS, null, MediaType.valueOf("application/problem+json")), "+json subtypes must be readable");
        Assertions.assertFalse(converter.canRead(STREAM_OF_BEANS, null, MediaType.TEXT_PLAIN), "unrelated media types must not be claimed");
    }

    @Test
    public void xmlConverterReadsStreamsFromXml() {
        final var converter = StreamHttpMessageConverter.forXml(new XmlMapper());
        Assertions.assertTrue(converter.canRead(STREAM_OF_BEANS, null, MediaType.APPLICATION_XML), "application/xml must be readable");
        Assertions.assertTrue(converter.canRead(STREAM_OF_BEANS, null, MediaType.TEXT_XML), "text/xml must be readable");
        Assertions.assertFalse(converter.canRead(STREAM_OF_BEANS, null, MediaType.APPLICATION_JSON), "json must not be claimed by the xml converter");
    }

    @Test
    public void onlyStreamTargetsAreClaimed() {
        final var converter = StreamHttpMessageConverter.forJson(new JsonMapper());
        Assertions.assertFalse(converter.canRead(LIST_OF_BEANS, null, MediaType.APPLICATION_JSON), "a List target must be left to other converters");
        Assertions.assertFalse(converter.canRead(Bean.class, MediaType.APPLICATION_JSON), "a plain class target must be left to other converters");
    }

    @Test
    public void converterNeverWrites() {
        final var converter = StreamHttpMessageConverter.forJson(new JsonMapper());
        Assertions.assertFalse(converter.canWrite(STREAM_OF_BEANS, null, MediaType.APPLICATION_JSON), "the converter is read-only");
    }

    @Test
    public void readsAJsonArrayElementByElement() throws IOException {
        final var converter = StreamHttpMessageConverter.forJson(new JsonMapper());
        final var message = new MockHttpInputMessage(new TrackingInputStream("""
            [{"key": "k1", "value": "v1"}, {"key": "k2", "value": "v2"}]
            """));
        try (final var got = converter.read(STREAM_OF_BEANS, null, message)) {
            Assertions.assertEquals(List.of(new Bean("k1", "v1"), new Bean("k2", "v2")), got.toList(), "every array element must be mapped to the streamed type");
        }
    }

    @Test
    public void malformedElementsFailWhileTheStreamIsConsumed() throws IOException {
        final var converter = StreamHttpMessageConverter.forJson(new JsonMapper());
        final var message = new MockHttpInputMessage(new TrackingInputStream("""
            [{"key": "k1", "value": "v1"}, {"key": ]
            """));
        try (final var got = converter.read(STREAM_OF_BEANS, null, message)) {
            Assertions.assertThrows(JacksonException.class, got::toList, "a malformed element must fail the consumer, as an unchecked jackson exception");
        }
    }

    @Test
    public void closingTheStreamClosesTheBody() throws IOException {
        final var converter = StreamHttpMessageConverter.forJson(new JsonMapper());
        final var body = new TrackingInputStream("""
            {"key": "k1", "value": "v1"}
            {"key": "k2", "value": "v2"}
            """);
        final var got = converter.read(STREAM_OF_BEANS, null, new MockHttpInputMessage(body));
        Assertions.assertEquals(new Bean("k1", "v1"), got.findFirst().orElseThrow(), "the first json line must be readable");
        Assertions.assertFalse(body.closed, "the body must stay open while the stream is in use");
        got.close();
        Assertions.assertTrue(body.closed, "closing the stream must close the body, releasing the connection");
    }
}
