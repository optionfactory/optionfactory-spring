package net.optionfactory.spring.upstream.rendering;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UriRedactorTest {

    @Test
    public void missingQueryParamIsNotRedacted() {
        final var redactor = new UriRedactor(Map.of("param", "@redacted@"));
        final var result = redactor.redact(URI.create("https://example.com"));
        Assertions.assertEquals(URI.create("https://example.com"), result);
    }

    @Test
    public void canRedactQueryParam() {
        final var redactor = new UriRedactor(Map.of("param", "@redacted@"));
        final var result = redactor.redact(URI.create("https://example.com?param=value"));
        Assertions.assertEquals(URI.create("https://example.com?param=@redacted@"), result);
    }
}
