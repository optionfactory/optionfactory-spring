package net.optionfactory.spring.upstream.contexts;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

public class ResponseContextTest {

    private static MockClientHttpResponse response(String body) {
        return new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
    }

    private static String text(BodySource source) {
        return new String(source.bytes(), StandardCharsets.UTF_8);
    }

    private static ResponseContext context(BodySource body) {
        return new ResponseContext(Instant.EPOCH, HttpStatus.OK, "OK", new HttpHeaders(), body, false);
    }

    @Test
    public void bufferedBodyCanBeInspected() {
        final var source = BodySource.of(response("content"), Buffering.BUFFERED);
        Assertions.assertSame(source, source.forInspection(true), "a buffered body must be inspectable as is");
    }

    @Test
    public void unbufferedBodyCannotBeInspected() {
        final var source = BodySource.of(response("content"), Buffering.UNBUFFERED_STREAMING);
        Assertions.assertThrows(IllegalStateException.class, () -> source.forInspection(true), "inspecting an unbuffered body must fail when asked to");
        Assertions.assertEquals("<unavailable>", text(source.forInspection(false)), "an unbuffered body must be replaced by a placeholder otherwise");
        Assertions.assertEquals("content", text(source), "the placeholder must leave the actual body unread for its consumer");
    }

    @Test
    public void detachingABufferedBodyCopiesIt() throws IOException {
        final var inner = response("content");
        final var detached = BodySource.of(inner, Buffering.BUFFERED).detached();
        inner.getBody().close();
        Assertions.assertEquals("content", text(detached), "a detached body must outlive the response it was read from");
        Assertions.assertSame(detached, detached.detached(), "detaching an in-memory body must be a no-op");
    }

    @Test
    public void detachingAnUnbufferedBodyYieldsAPlaceholder() {
        final var detached = BodySource.of(response("content"), Buffering.UNBUFFERED).detached();
        Assertions.assertEquals("<unavailable>", text(detached), "an unbuffered body must not be consumed by detaching");
    }

    @Test
    public void inMemoryBodiesAreAlwaysInspectable() throws IOException {
        final var source = BodySource.of("content", StandardCharsets.UTF_8);
        Assertions.assertSame(source, source.forInspection(true), "an in-memory body must always be inspectable");
        Assertions.assertEquals("content", new String(source.getInputStream().readAllBytes(), StandardCharsets.UTF_8), "an in-memory body must be readable as a stream");
        Assertions.assertEquals("content", new String(source.getInputStream().readAllBytes(), StandardCharsets.UTF_8), "an in-memory body must be readable more than once");
    }

    @Test
    public void withAlertFlagsTheResponseAndKeepsTheRest() {
        final var body = BodySource.of(new byte[0]);
        final var flagged = context(body).withAlert();
        Assertions.assertTrue(flagged.alert(), "withAlert must flag the response");
        Assertions.assertSame(body, flagged.body(), "withAlert must keep the same body");
        Assertions.assertEquals(HttpStatus.OK, flagged.status(), "withAlert must keep the status");
    }

    @Test
    public void detachedResponseCarriesADetachedBody() {
        final var detached = context(BodySource.of(response("content"), Buffering.BUFFERED)).detached();
        Assertions.assertInstanceOf(ResponseContext.ByteArrayBodySource.class, detached.body(), "a detached response must carry an in-memory body");
        Assertions.assertEquals("content", text(detached.body()), "a detached response must carry the body content");
    }

    @Test
    public void withUriReplacesOnlyTheUri() {
        final var headers = new HttpHeaders();
        final var original = new RequestContext(Instant.EPOCH, HttpMethod.GET, URI.create("http://example.com/a"), headers, Map.of(), new byte[0]);
        final var changed = original.withUri(URI.create("http://example.com/b"));
        Assertions.assertEquals(URI.create("http://example.com/b"), changed.uri(), "withUri must replace the uri");
        Assertions.assertSame(headers, changed.headers(), "withUri must share the same headers");
        Assertions.assertEquals(URI.create("http://example.com/a"), original.uri(), "the original request must be left untouched");
    }
}
