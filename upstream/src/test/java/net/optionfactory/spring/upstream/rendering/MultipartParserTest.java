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
            () -> Assertions.assertEquals(3, parts.size()),
            () -> Assertions.assertEquals("jdoe", new String(parts.get(0).body(), StandardCharsets.UTF_8)),
            () -> Assertions.assertEquals("form-data; name=\"username\"", parts.get(0).headers().getFirst("Content-Disposition")),
            () -> Assertions.assertEquals("admin", new String(parts.get(1).body(), StandardCharsets.UTF_8)),
            () -> Assertions.assertEquals("form-data; name=\"role\"", parts.get(1).headers().getFirst("Content-Disposition")),
            () -> Assertions.assertEquals("true", new String(parts.get(2).body(), StandardCharsets.UTF_8)),
            () -> Assertions.assertEquals("form-data; name=\"active\"", parts.get(2).headers().getFirst("Content-Disposition"))        
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
            () -> Assertions.assertEquals(2, parts.size()),
            () -> Assertions.assertEquals("application/json", parts.get(0).headers().getFirst("Content-Type")),
            () -> Assertions.assertEquals("{\"id\": 101, \"name\": \"Sensor\"}", new String(parts.get(0).body(), StandardCharsets.UTF_8)),
            () -> Assertions.assertEquals("image/svg+xml", parts.get(1).headers().getFirst("Content-Type")),
            () -> Assertions.assertEquals("<svg width=\"100\" height=\"100\"><circle cx=\"50\" cy=\"50\" r=\"40\" /></svg>", new String(parts.get(1).body(), StandardCharsets.UTF_8))
        );
        
    }

}
