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
        Assertions.assertEquals(URI.create("https://example.com"), result, "a uri without the configured param must be returned as it is");
    }

    @Test
    public void canRedactQueryParam() {
        final var redactor = new UriRedactor(Map.of("param", "@redacted@"));
        final var result = redactor.redact(URI.create("https://example.com?param=value"));
        Assertions.assertEquals(URI.create("https://example.com?param=@redacted@"), result, "the configured param must have its value replaced");
    }

    @Test
    public void aUriNeedingNoRedactionIsReturnedAsItIs() {
        final var source = URI.create("https://example.com/path?other=value");
        Assertions.assertSame(source, new UriRedactor(Map.of("param", "R")).redact(source), "a uri without configured params must be the very same instance");
    }

    @Test
    public void allValuesOfARepeatedParamCollapseIntoOneKeepingTheOthers() {
        final var result = new UriRedactor(Map.of("param", "R")).redact(URI.create("https://example.com/path?param=a&other=b&param=c"));
        Assertions.assertEquals(URI.create("https://example.com/path?param=R&other=b"), result, "a repeated param must be rendered once, redacted where it first appears, and the other params kept");
    }

    @Test
    public void paramNamesAreCaseSensitive() {
        final var source = URI.create("https://example.com/path?PARAM=a");
        Assertions.assertSame(source, new UriRedactor(Map.of("param", "R")).redact(source), "a param differing in case must not be redacted");
    }

    @Test
    public void percentEscapesAreKeptAsTheyAre() {
        final var result = new UriRedactor(Map.of("param", "R")).redact(URI.create("https://example.com/a%20b?q=a%20b&param=x%20y#frag%20ment"));
        Assertions.assertEquals(URI.create("https://example.com/a%20b?q=a%20b&param=R#frag%20ment"), result, "escapes already in the uri must not be encoded again");
    }

    @Test
    public void theReplacementIsEncoded() {
        final var result = new UriRedactor(Map.of("param", "a b&c")).redact(URI.create("https://example.com/?param=x"));
        Assertions.assertEquals(URI.create("https://example.com/?param=a%20b%26c"), result, "the replacement must be encoded as a query param value");
    }
}
