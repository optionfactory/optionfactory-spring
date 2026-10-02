package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

public class FormUrlencodedRedactorTest {

    @Test
    public void missingRequesrParamIsNotRedacted() {
        final var redactor = new FormUrlencodedRedactor(Map.of("param", "@redacted@"));
        final var result = redactor.redact(new ByteArrayResource("param1=123&param2=value".getBytes(StandardCharsets.UTF_8)));
        Assertions.assertEquals("param1=123&param2=value", result, "params that are not configured must be kept, names matching exactly");
    }

    @Test
    public void canRedactRequestParam() {
        final var redactor = new FormUrlencodedRedactor(Map.of("param", "@redacted@"));
        final var result = redactor.redact(new ByteArrayResource("param1=123&param=value".getBytes(StandardCharsets.UTF_8)));
        Assertions.assertEquals("param1=123&param=@redacted@", result, "a configured param must have its value replaced");
    }

    @Test
    public void allValuesOfARepeatedParamCollapseIntoOne() {
        final var redactor = new FormUrlencodedRedactor(Map.of("param", "R"));
        final var result = redactor.redact(new ByteArrayResource("param=a&other=b&param=c".getBytes(StandardCharsets.UTF_8)));
        Assertions.assertEquals("param=R&other=b", result, "a repeated param must be rendered once, redacted");
    }

    @Test
    public void aBlankBodyIsRenderedEmpty() {
        final var redactor = new FormUrlencodedRedactor(Map.of("param", "R"));
        Assertions.assertEquals("", redactor.redact(new ByteArrayResource(" \n".getBytes(StandardCharsets.UTF_8))), "a blank body must render as an empty string");
    }
}
