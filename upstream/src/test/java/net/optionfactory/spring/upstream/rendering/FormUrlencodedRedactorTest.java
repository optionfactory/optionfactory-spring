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
        Assertions.assertEquals("param1=123&param2=value", result);
    }

    @Test
    public void canRedactRequestParam() {
        final var redactor = new FormUrlencodedRedactor(Map.of("param", "@redacted@"));
        final var result = redactor.redact(new ByteArrayResource("param1=123&param=value".getBytes(StandardCharsets.UTF_8)));
        Assertions.assertEquals("param1=123&param=@redacted@", result);
    }
}
