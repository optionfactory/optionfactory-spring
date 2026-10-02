package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.core.JsonPointer;
import tools.jackson.databind.json.JsonMapper;

public class JsonRedactorTest {

    @Test
    public void canRedactJson() throws Exception {
        final var redactor = new JsonRedactor(new JsonMapper(), Map.of(
                JsonPointer.compile("/password"), "<redacted>",
                JsonPointer.compile("/nested/password"), "<redacted>"
        ));
        final var input = """
            {
                "password": "secret",
                "nested": {
                    "password": "secret"
                }
            }
        """;
        final var got = redactor.redact(new ByteArrayResource(input.getBytes(StandardCharsets.UTF_8)));
        final var expected = """
        {"password":"<redacted>","nested":{"password":"<redacted>"}}
        """;

        Assertions.assertEquals(expected.strip(), got, "the addressed values must be redacted and the document compacted");
    }

    private static String redact(Map<JsonPointer, String> pointers, String input) {
        return new JsonRedactor(new JsonMapper(), pointers).redact(new ByteArrayResource(input.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void canRedactArrayElements() {
        final var got = redact(Map.of(JsonPointer.compile("/tokens/1"), "R"), "{\"tokens\": [\"a\", \"b\", \"c\"]}");
        Assertions.assertEquals("{\"tokens\":[\"a\",\"R\",\"c\"]}", got, "a pointer to an array element must redact that element only");
    }

    @Test
    public void aContainerIsReplacedWholeByTheReplacement() {
        final var got = redact(Map.of(JsonPointer.compile("/credentials"), "R"), "{\"credentials\": {\"user\": \"u\", \"secret\": \"s\"}, \"n\": 1}");
        Assertions.assertEquals("{\"credentials\":\"R\",\"n\":1}", got, "an addressed object must be replaced whole by the replacement string");
    }

    @Test
    public void aPointerAddressingNothingIsIgnored() {
        final var got = redact(Map.of(JsonPointer.compile("/missing/secret"), "R"), "{ \"a\" : 1 }");
        Assertions.assertEquals("{\"a\":1}", got, "a pointer addressing nothing must leave the document unchanged, if compacted");
    }

    @Test
    public void escapedPointerSegmentsAddressTheUnescapedField() {
        final var got = redact(Map.of(JsonPointer.compile("/a~1b"), "R", JsonPointer.compile("/c~0d"), "R"), "{\"a/b\": \"s\", \"c~d\": \"s\"}");
        Assertions.assertEquals("{\"a/b\":\"R\",\"c~d\":\"R\"}", got, "~1 and ~0 must address the fields holding / and ~");
    }

    @Test
    public void theRootPointerRedactsTheWholeDocument() {
        final var got = redact(Map.of(JsonPointer.compile(""), "R"), "{\"a\": \"s\"}");
        Assertions.assertEquals("\"R\"", got, "the root pointer must replace the whole document");
    }
}
