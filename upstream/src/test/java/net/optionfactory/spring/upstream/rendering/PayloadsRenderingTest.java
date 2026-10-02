package net.optionfactory.spring.upstream.rendering;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.BodiesStrategy;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.HeadersStrategy;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.MultipartStrategy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class PayloadsRenderingTest {

    private final PayloadsRendering br = PayloadsRendering.builder().build();



    @Test
    public void xmlPreambleIsRemoved() {
        final var source = """
                           <?xml version="1.0" encoding="utf-8"?>
                           <a/>
                           """;
        final var got = br.renderBody(BodiesStrategy.ABBREVIATED_REDACTED, 0, MediaType.APPLICATION_XML, BodySource.of(source, StandardCharsets.UTF_8), "✂", 100_000);
        Assertions.assertEquals("<a/>", got, "the xml declaration must be dropped");
    }

    @Test
    public void canCompactXml() {
        final var source = """
                           <?xml version="1.0" encoding="iso-8859-1"?>
                           <a>
                           <b>c</b>
                           </a>
                           """;
        final var got = br.renderBody(BodiesStrategy.ABBREVIATED_REDACTED, 0, MediaType.APPLICATION_XML, BodySource.of(source, StandardCharsets.ISO_8859_1), "✂", 100_000);
        Assertions.assertEquals("<a><b>c</b></a>", got, "an xml body must be compacted, whatever its declared encoding");

    }

    @Test
    public void textNodesSpacesAreNormalized() {
        final var source = """
                           <a> a 
                                b    
                                    c 
                           </a>
                           """;
        final var got = br.renderBody(BodiesStrategy.ABBREVIATED_REDACTED, 0, MediaType.APPLICATION_XML, BodySource.of(source, StandardCharsets.UTF_8), "✂", 100_000);
        Assertions.assertEquals("<a>a b c</a>", got, "the spaces of xml text must be normalized");

    }

    @Test
    public void emptyElementsAreCollapsed() {
        final var source = """
            <a></a>
        """;
        final var got = br.renderBody(BodiesStrategy.ABBREVIATED_REDACTED, 0, MediaType.APPLICATION_XML, BodySource.of(source, StandardCharsets.UTF_8), "✂", 100_000);
        Assertions.assertEquals("<a/>", got, "an empty xml element must be collapsed");

    }

    @Test
    public void canCompactJson() {
        final var source = """
        {
             "a": "b",
             "c": 1
        }
        """;
        final var got = br.renderBody(BodiesStrategy.ABBREVIATED_REDACTED, 0, MediaType.APPLICATION_JSON, BodySource.of(source, StandardCharsets.UTF_8), "✂", 100_000);
        Assertions.assertEquals("{\"a\":\"b\",\"c\":1}", got, "a json body must be compacted");

    }

    @Test
    public void canRenderAbbreviatedCompactJsonMediaTypes() {
        final var source = BodySource.of("[1, 2]", StandardCharsets.UTF_8);

        Assertions.assertEquals("[1,2]", br.renderBody(PayloadsRendering.BodiesStrategy.ABBREVIATED_REDACTED, 6, MediaType.APPLICATION_JSON, source, ".", 2048), "application/json must be compacted");
        Assertions.assertEquals("[1,2]", br.renderBody(PayloadsRendering.BodiesStrategy.ABBREVIATED_REDACTED, 6, MediaType.APPLICATION_PROBLEM_JSON, source, ".", 2048), "a +json media type must be compacted");
        Assertions.assertEquals("[1, 2]", br.renderBody(PayloadsRendering.BodiesStrategy.ABBREVIATED_REDACTED, 6, MediaType.parseMediaType("application/unsupported"), source, ".", 2048), "an unknown media type must be rendered as it is");

    }

    @Test
    public void canRenderAbbreviatedCompactXMLMediaTypes() {
        final var source = BodySource.of("<a> <b/> </a>", StandardCharsets.UTF_8);

        Assertions.assertEquals("<a><b/></a>", br.renderBody(PayloadsRendering.BodiesStrategy.ABBREVIATED_REDACTED, 6, MediaType.APPLICATION_XML, source, ".", 2048), "application/xml must be compacted");
        Assertions.assertEquals("<a><b/></a>", br.renderBody(PayloadsRendering.BodiesStrategy.ABBREVIATED_REDACTED, 6, MediaType.parseMediaType("application/soap+xml"), source, ".", 2048), "a +xml media type must be compacted");
        Assertions.assertEquals("<a> <b/> </a>", br.renderBody(PayloadsRendering.BodiesStrategy.ABBREVIATED_REDACTED, 6, MediaType.parseMediaType("application/unsupported"), source, ".", 2048), "an unknown media type must be rendered as it is");

    }

    @Test
    public void malformedJsonIsRenderedRawWhenRedactionFails() {
        final var source = BodySource.of("{\"password\": \"s3cret\"", StandardCharsets.UTF_8);
        final var got = br.renderBody(BodiesStrategy.ABBREVIATED_REDACTED, 21, MediaType.APPLICATION_JSON, source, ".", 2048);
        Assertions.assertEquals("{\"password\": \"s3cret\"", got, "redaction failure fails open by choice: the raw payload wins over a placeholder");
    }

    @Test
    public void malformedXmlIsRenderedRawWhenRedactionFails() {
        final var source = BodySource.of("<a><password>s3cret</a>", StandardCharsets.UTF_8);
        final var got = br.renderBody(BodiesStrategy.ABBREVIATED_REDACTED, 23, MediaType.APPLICATION_XML, source, ".", 2048);
        Assertions.assertEquals("<a><password>s3cret</a>", got, "redaction failure fails open by choice: the raw payload wins over a placeholder");
    }

    private static String render(PayloadsRendering rendering, BodiesStrategy strategy, long contentLength, MediaType type, String body, int maxSize) {
        return rendering.renderBody(strategy, contentLength, type, BodySource.of(body, StandardCharsets.UTF_8), "~", maxSize);
    }

    @Test
    public void skipRendersNothingAndSizeRendersTheDeclaredLength() {
        Assertions.assertEquals("", render(br, BodiesStrategy.SKIP, 3, MediaType.TEXT_PLAIN, "abc", 100), "SKIP must render an empty string");
        Assertions.assertEquals("size: 3B", render(br, BodiesStrategy.SIZE, 3, MediaType.TEXT_PLAIN, "abc", 100), "SIZE must render the declared length");
        Assertions.assertEquals("size: <unavailable>", render(br, BodiesStrategy.SIZE, -1, MediaType.TEXT_PLAIN, "abc", 100), "SIZE without a declared length must say it is unavailable");
    }

    @Test
    public void binaryBodiesAreDescribedRatherThanRendered() {
        final var binary = BodySource.of(new byte[]{1, 0, 2});
        Assertions.assertEquals("<binary> size: 3B", br.renderBody(BodiesStrategy.ABBREVIATED, 3, MediaType.APPLICATION_OCTET_STREAM, binary, "~", 100), "ABBREVIATED must describe a binary body");
        Assertions.assertEquals("<binary> size: <unavailable>", br.renderBody(BodiesStrategy.ABBREVIATED_REDACTED, -1, MediaType.APPLICATION_OCTET_STREAM, binary, "~", 100), "ABBREVIATED_REDACTED must describe a binary body");
    }

    @Test
    public void abbreviatedRendersTheBodyAsItIs() {
        final var redacting = PayloadsRendering.builder().jsonPtr("/password").build();
        Assertions.assertEquals("{\"password\": \"s\"}", render(redacting, BodiesStrategy.ABBREVIATED, -1, MediaType.APPLICATION_JSON, "{\"password\": \"s\"}", 100), "ABBREVIATED must neither redact nor compact");
        Assertions.assertEquals("abc~hij", render(redacting, BodiesStrategy.ABBREVIATED, -1, MediaType.TEXT_PLAIN, "abcdefghij", 7), "ABBREVIATED must abbreviate to maxSize");
    }

    @Test
    public void abbreviatedRedactedRedactsJsonBeforeAbbreviating() {
        final var redacting = PayloadsRendering.builder().jsonPtr("/password").build();
        Assertions.assertEquals("{\"password\":\"@redacted@\",\"user\":\"u\"}", render(redacting, BodiesStrategy.ABBREVIATED_REDACTED, -1, MediaType.APPLICATION_JSON, "{\"password\": \"s\", \"user\": \"u\"}", 1000), "json pointers must be redacted with the default replacement");
        Assertions.assertEquals("{\"passwo~r\":\"u\"}", render(redacting, BodiesStrategy.ABBREVIATED_REDACTED, -1, MediaType.APPLICATION_JSON, "{\"password\": \"s\", \"user\": \"u\"}", 16), "the redacted body must then be abbreviated");
    }

    @Test
    public void abbreviatedRedactedRedactsFormBodies() {
        final var redacting = PayloadsRendering.builder().param("password", "R").build();
        Assertions.assertEquals("user=u&password=R", render(redacting, BodiesStrategy.ABBREVIATED_REDACTED, -1, MediaType.APPLICATION_FORM_URLENCODED, "user=u&password=s", 1000), "form params must be redacted");
    }

    @Test
    public void bodiesOfOtherTypesAreRenderedOnASingleLine() {
        Assertions.assertEquals("line oneline two", render(br, BodiesStrategy.ABBREVIATED_REDACTED, -1, MediaType.TEXT_PLAIN, "line one\r\nline two\n", 1000), "line breaks must be removed from a body with no redactor");
    }

    @Test
    public void aMalformedJsonPointerIsRejectedAtConfiguration() {
        final var builder = PayloadsRendering.builder();
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.jsonPtr("password"), "a json pointer not starting with '/' must be rejected");
    }

    @Test
    public void requestsHaveTheirUriAndHeadersRedacted() {
        final var redacting = PayloadsRendering.builder().param("token").header("Authorization").build();
        final var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer secret");
        headers.setContentType(MediaType.TEXT_PLAIN);
        final var request = new RequestContext(Instant.EPOCH, HttpMethod.POST, URI.create("https://example.com/a?token=secret&page=1"), headers, Map.of(), "body".getBytes(StandardCharsets.UTF_8));
        final var got = redacting.render(request, MultipartStrategy.RENDER_PARTS, HeadersStrategy.SKIP, BodiesStrategy.ABBREVIATED_REDACTED, "~", 1000);
        Assertions.assertEquals(URI.create("https://example.com/a?token=@redacted@&page=1"), got.uri(), "the configured query param must be redacted in place");
        Assertions.assertEquals("@redacted@", got.main().headers().getFirst("Authorization"), "request headers must be redacted whatever the headers strategy");
        Assertions.assertEquals("body", got.main().body(), "the request body must be rendered");
        Assertions.assertTrue(got.parts().isEmpty(), "a request that is not multipart must have no parts");
    }

    private static ResponseContext response(HttpHeaders headers, String body) {
        return new ResponseContext(Instant.EPOCH, HttpStatus.OK, "OK", headers, BodySource.of(body, StandardCharsets.UTF_8), false);
    }

    @Test
    public void responseHeadersAreRedactedOnlyWhenRendered() {
        final var redacting = PayloadsRendering.builder().header("Set-Cookie").build();
        final var headers = new HttpHeaders();
        headers.set("Set-Cookie", "session=secret");
        final var skipped = redacting.render(response(headers, ""), MultipartStrategy.RENDER_PARTS, HeadersStrategy.SKIP, BodiesStrategy.SKIP, "~", 1000);
        Assertions.assertEquals("session=secret", skipped.main().headers().getFirst("Set-Cookie"), "with SKIP the headers must be returned as they are, not to be logged");
        final var rendered = redacting.render(response(headers, ""), MultipartStrategy.RENDER_PARTS, HeadersStrategy.CONTENT, BodiesStrategy.SKIP, "~", 1000);
        Assertions.assertEquals("@redacted@", rendered.main().headers().getFirst("Set-Cookie"), "with CONTENT the headers must be redacted");
    }

    @Test
    public void multipartResponsesAreRenderedPartByPart() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("multipart/mixed; boundary=b"));
        final var body = "--b\r\nContent-Type: text/plain\r\n\r\nfirst\r\n--b\r\nContent-Type: text/plain\r\n\r\nsecond\r\n--b--";
        final var got = br.render(response(headers, body), MultipartStrategy.RENDER_PARTS, HeadersStrategy.SKIP, BodiesStrategy.ABBREVIATED_REDACTED, "~", 1000);
        Assertions.assertEquals("", got.main().body(), "the main body of a multipart response must be empty");
        Assertions.assertEquals(2, got.parts().size(), "every part must be rendered");
        Assertions.assertEquals("first", got.parts().get(0).body(), "the first part body must be rendered");
        Assertions.assertEquals("second", got.parts().get(1).body(), "the second part body must be rendered");
    }

    @Test
    public void aMultipartPayloadWithoutPartsIsMarkedMalformed() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        final var got = br.render(response(headers, "garbage"), MultipartStrategy.RENDER_PARTS, HeadersStrategy.SKIP, BodiesStrategy.ABBREVIATED_REDACTED, "~", 1000);
        Assertions.assertEquals("<malformed-multipart>", got.main().body(), "a multipart payload that cannot be parsed must be marked as malformed");
        Assertions.assertTrue(got.parts().isEmpty(), "a malformed multipart payload must have no parts");
    }

    @Test
    public void anUnbufferedResponseBodyIsNotConsumed() {
        final var streamed = new MockClientHttpResponse(HttpStatus.OK, "OK", new HttpHeaders(), new ByteArrayResource("streamed".getBytes(StandardCharsets.UTF_8)));
        final var response = new ResponseContext(Instant.EPOCH, HttpStatus.OK, "OK", new HttpHeaders(), BodySource.of(streamed, Buffering.UNBUFFERED), false);
        final var got = br.render(response, MultipartStrategy.RENDER_PARTS, HeadersStrategy.SKIP, BodiesStrategy.ABBREVIATED_REDACTED, "~", 1000);
        Assertions.assertEquals("<unavailable>", got.main().body(), "a streamed body must be rendered as unavailable");
    }

    @Test
    public void multipartPartsAreRedactedByTheirOwnContentType() {
        final var redacting = PayloadsRendering.builder().jsonPtr("/password").build();
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("multipart/mixed; boundary=b"));
        final var body = "--b\r\nContent-Type: application/json\r\n\r\n{\"password\": \"s3cret\"}\r\n--b--";
        final var got = redacting.render(response(headers, body), MultipartStrategy.RENDER_PARTS, HeadersStrategy.SKIP, BodiesStrategy.ABBREVIATED_REDACTED, "~", 1000);
        Assertions.assertEquals("{\"password\":\"@redacted@\"}", got.parts().get(0).body(), "a json part must be redacted as json, by its own Content-Type");
    }

    @Test
    public void formBodiesWithACharsetAreRedacted() {
        final var redacting = PayloadsRendering.builder().param("password", "R").build();
        final var type = MediaType.parseMediaType("application/x-www-form-urlencoded;charset=UTF-8");
        Assertions.assertEquals("user=u&password=R", render(redacting, BodiesStrategy.ABBREVIATED_REDACTED, -1, type, "user=u&password=s", 1000), "form params must be redacted whatever the media type parameters");
    }

    @Test
    public void recapRendersThePartsWithTheirHeadersAndSizeOnly() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("multipart/mixed; boundary=b"));
        final var body = "--b\r\nContent-Type: text/plain\r\n\r\nfirst\r\n--b\r\nContent-Type: text/plain\r\n\r\nsecond\r\n--b--";
        final var got = br.render(response(headers, body), MultipartStrategy.RENDER_RECAP, HeadersStrategy.SKIP, BodiesStrategy.ABBREVIATED_REDACTED, "~", 1000);
        Assertions.assertEquals(2, got.parts().size(), "every part must be recapped");
        Assertions.assertEquals(MediaType.TEXT_PLAIN, got.parts().get(0).headers().getContentType(), "a recapped part must keep its headers");
        Assertions.assertEquals("size: 5B", got.parts().get(0).body(), "a recapped part body must be rendered as its size");
        Assertions.assertEquals("size: 6B", got.parts().get(1).body(), "a recapped part body must be rendered as its size");
    }
}
