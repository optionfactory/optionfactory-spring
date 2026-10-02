package net.optionfactory.spring.upstream.rendering;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

public class HttpHeadersRedactorTest {

    @Test
    public void missingHeaderIsNotRedacted() {
        final var redactor = new HttpHeadersRedactor(Map.of("authorization", "@redacted@"));
        final var result = redactor.redact(HttpHeaders.EMPTY);
        Assertions.assertEquals(HttpHeaders.EMPTY, result, "headers without the configured one must be returned as they are");
    }

    @Test
    public void canRedactHttpHeader() {
        final var redactor = new HttpHeadersRedactor(Map.of("authorization", "@redacted@"));
        final var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer MY_TOKEN");
        final var result = redactor.redact(headers);
        final var expected = new HttpHeaders();
        expected.set("Authorization", "@redacted@");
        Assertions.assertEquals(expected, result, "the configured header must be redacted, its name matched case-insensitively");
    }

    @Test
    public void allValuesOfARedactedHeaderCollapseIntoOne() {
        final var redactor = new HttpHeadersRedactor(Map.of("X-Secret", "R"));
        final var headers = new HttpHeaders();
        headers.add("X-Secret", "a");
        headers.add("X-Secret", "b");
        headers.add("X-Other", "c");
        final var result = redactor.redact(headers);
        Assertions.assertEquals(List.of("R"), result.get("X-Secret"), "every value of the header must be replaced by a single redacted one");
        Assertions.assertEquals(List.of("c"), result.get("X-Other"), "other headers must be kept");
    }

    @Test
    public void withoutRedactionsTheSourceIsReturned() {
        final var headers = new HttpHeaders();
        headers.add("X-Secret", "a");
        Assertions.assertSame(headers, new HttpHeadersRedactor(Map.of()).redact(headers), "without redactions the very same headers must be returned");
        Assertions.assertNull(new HttpHeadersRedactor(Map.of("X-Secret", "R")).redact(null), "null headers must be returned as null");
    }

    @Test
    public void redactingDoesNotChangeTheSourceHeaders() {
        final var redactor = new HttpHeadersRedactor(Map.of("Authorization", "R"));
        final var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer MY_TOKEN");
        final var result = redactor.redact(headers);
        Assertions.assertEquals("R", result.getFirst("Authorization"), "the returned headers must be redacted");
        Assertions.assertEquals("Bearer MY_TOKEN", headers.getFirst("Authorization"), "the source headers must keep the real value");
    }

}
