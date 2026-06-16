package net.optionfactory.spring.upstream.rendering;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

public class HttpHeadersRedactorTest {

    @Test
    public void missingHeaderIsNotRedacted() {
        final var redactor = new HttpHeadersRedactor(Map.of("authorization", "@redacted@"));
        final var result = redactor.redact(HttpHeaders.EMPTY);
        Assertions.assertEquals(HttpHeaders.EMPTY, result);
    }

    @Test
    public void canRedactHttpHeader() {
        final var redactor = new HttpHeadersRedactor(Map.of("authorization", "@redacted@"));
        final var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer MY_TOKEN");
        final var result = redactor.redact(headers);
        final var expected = new HttpHeaders();
        expected.set("Authorization", "@redacted@");
        Assertions.assertEquals(expected, result);
    }

}
