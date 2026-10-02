package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import java.util.List;
import net.optionfactory.spring.upstream.contexts.ResponseContext.ByteArrayBodySource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class MultipartParserTest {

    @Test
    public void canParseMultipleFormFields() {
        final var raw = String.join("\r\n", List.of(
                "--boundary-123",
                "Content-Disposition: form-data; name=\"username\"",
                "",
                "jdoe",
                "--boundary-123",
                "Content-Disposition: form-data; name=\"role\"",
                "",
                "admin",
                "--boundary-123",
                "Content-Disposition: form-data; name=\"active\"",
                "",
                "true",
                "--boundary-123--"
        ));

        final var bodySource = new ByteArrayBodySource(raw.getBytes(StandardCharsets.UTF_8));
        final var mediaType = MediaType.parseMediaType("multipart/form-data; boundary=boundary-123");
        final var parts = MultipartParser.parse(bodySource, mediaType);

        Assertions.assertAll(
            () -> Assertions.assertEquals(3, parts.size(), "every part up to the closing delimiter must be parsed"),
            () -> Assertions.assertEquals("jdoe", new String(parts.get(0).body(), StandardCharsets.UTF_8), "the first body must end before the next delimiter"),
            () -> Assertions.assertEquals("form-data; name=\"username\"", parts.get(0).headers().getFirst("Content-Disposition"), "the first part headers must be parsed"),
            () -> Assertions.assertEquals("admin", new String(parts.get(1).body(), StandardCharsets.UTF_8), "the second body must end before the next delimiter"),
            () -> Assertions.assertEquals("form-data; name=\"role\"", parts.get(1).headers().getFirst("Content-Disposition"), "the second part headers must be parsed"),
            () -> Assertions.assertEquals("true", new String(parts.get(2).body(), StandardCharsets.UTF_8), "the last body must end before the closing delimiter"),
            () -> Assertions.assertEquals("form-data; name=\"active\"", parts.get(2).headers().getFirst("Content-Disposition"), "the last part headers must be parsed")        
        );
    }

    @Test
    public void canParseJsonAndSvgParts() {
        final var raw = String.join("\r\n", List.of(
                "--boundary-123",
                "Content-Disposition: form-data; name=\"metadata\"",
                "Content-Type: application/json",
                "",
                "{\"id\": 101, \"name\": \"Sensor\"}",
                "--boundary-123",
                "Content-Disposition: form-data; name=\"icon\"",
                "Content-Type: image/svg+xml",
                "",
                "<svg width=\"100\" height=\"100\"><circle cx=\"50\" cy=\"50\" r=\"40\" /></svg>",
                "--boundary-123--"
        ));

        final var bodySource = new ByteArrayBodySource(raw.getBytes(StandardCharsets.UTF_8));
        final var mediaType = MediaType.parseMediaType("multipart/form-data; boundary=boundary-123");
        final var parts = MultipartParser.parse(bodySource, mediaType);
        
        Assertions.assertAll(
            () -> Assertions.assertEquals(2, parts.size(), "both parts must be parsed"),
            () -> Assertions.assertEquals("application/json", parts.get(0).headers().getFirst("Content-Type"), "the json part Content-Type must be parsed"),
            () -> Assertions.assertEquals("{\"id\": 101, \"name\": \"Sensor\"}", new String(parts.get(0).body(), StandardCharsets.UTF_8), "the json part body must be kept as it is"),
            () -> Assertions.assertEquals("image/svg+xml", parts.get(1).headers().getFirst("Content-Type"), "the svg part Content-Type must be parsed"),
            () -> Assertions.assertEquals("<svg width=\"100\" height=\"100\"><circle cx=\"50\" cy=\"50\" r=\"40\" /></svg>", new String(parts.get(1).body(), StandardCharsets.UTF_8), "the svg part body must be kept as it is")
        );
    }

    private static MediaType multipart(String boundary) {
        return MediaType.parseMediaType("multipart/form-data; boundary=" + boundary);
    }

    @Test
    public void theMediaTypeMustCarryABoundary() {
        final var bodySource = new ByteArrayBodySource(new byte[0]);
        Assertions.assertThrows(IllegalArgumentException.class, () -> MultipartParser.parse(bodySource, MediaType.MULTIPART_FORM_DATA), "a multipart media type without boundary must be rejected");
    }

    @Test
    public void thePreambleIsIgnoredAndAMissingClosingDelimiterRunsToTheEnd() {
        final var raw = String.join("\r\n", List.of(
                "preamble",
                "--b",
                "Content-Disposition: form-data; name=\"only\"",
                "not a header",
                "",
                "value"
        ));
        final var parts = MultipartParser.parse(new ByteArrayBodySource(raw.getBytes(StandardCharsets.UTF_8)), multipart("b"));
        Assertions.assertEquals(1, parts.size(), "the preamble must not be taken for a part");
        Assertions.assertEquals("value", new String(parts.get(0).body(), StandardCharsets.UTF_8), "a body without a following delimiter must run to the end of the payload");
        Assertions.assertEquals(1, parts.get(0).headers().size(), "a header line without ':' must be skipped");
    }

    @Test
    public void aPayloadWithoutDelimitersHasNoParts() {
        final var parts = MultipartParser.parse(new ByteArrayBodySource("no delimiters here".getBytes(StandardCharsets.UTF_8)), multipart("b"));
        Assertions.assertTrue(parts.isEmpty(), "a payload without delimiters must yield no parts");
    }

    @Test
    public void binaryBodiesAreKeptByteForByte() {
        final var head = "--b\r\nContent-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        final var body = new byte[]{0, (byte) 0xFF, '\r', '\n', 1};
        final var tail = "\r\n--b--".getBytes(StandardCharsets.ISO_8859_1);
        final var raw = new byte[head.length + body.length + tail.length];
        System.arraycopy(head, 0, raw, 0, head.length);
        System.arraycopy(body, 0, raw, head.length, body.length);
        System.arraycopy(tail, 0, raw, head.length + body.length, tail.length);
        final var parts = MultipartParser.parse(new ByteArrayBodySource(raw), multipart("b"));
        Assertions.assertArrayEquals(body, parts.get(0).body(), "a binary body, line breaks included, must be returned unchanged");
    }

    @Test
    public void detectsMultipartTypesOnly() {
        Assertions.assertTrue(MultipartParser.isMultipart(MediaType.parseMediaType("MULTIPART/mixed")), "any multipart type must be recognized, regardless of case");
        Assertions.assertFalse(MultipartParser.isMultipart(MediaType.APPLICATION_JSON), "a non multipart type must not be recognized");
        Assertions.assertFalse(MultipartParser.isMultipart(null), "a missing media type must not be multipart");
    }

}
