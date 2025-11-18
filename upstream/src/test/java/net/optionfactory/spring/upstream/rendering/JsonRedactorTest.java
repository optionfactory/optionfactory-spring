package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
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

        Assert.assertEquals(expected.strip(), got);
    }
}
