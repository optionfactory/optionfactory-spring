package net.optionfactory.spring.upstream.rendering;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ByteArrayResource;

public class JsonRedactorTest {

    @Test
    public void canRedactJson() throws Exception {
        JsonRedactor redactor = new JsonRedactor(new ObjectMapper(), List.of(
                JsonPointer.compile("/password"),
                JsonPointer.compile("/nested/password")
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
