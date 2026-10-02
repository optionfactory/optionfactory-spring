package net.optionfactory.spring.upstream.alerts.spooler;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;

public class AlertBodiesFunctionsTest {

    private final AlertBodiesFunctions bodies = new AlertBodiesFunctions();

    private static InvocationContext invocation(PayloadsRendering rendering) throws NoSuchMethodException {
        return new InvocationContext(
                new Expressions(null, null),
                rendering,
                new InvocationContext.MessageConverters(HttpMessageConverters.forClient().build()),
                new EndpointDescriptor("upstream", "endpoint", Object.class.getMethod("toString"), null),
                new Object[0],
                "boot",
                0,
                null,
                Buffering.BUFFERED);
    }

    private static RequestContext request(HttpHeaders headers, String body) {
        return new RequestContext(Instant.now(), HttpMethod.POST, URI.create("https://example.com"), headers, new HashMap<>(), body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void longRequestBodiesAreAbbreviatedAroundScissors() throws Exception {
        final var rendered = bodies.abbreviated(invocation(PayloadsRendering.builder().build()), request(new HttpHeaders(), "0123456789".repeat(10)), 20);

        final var body = rendered.main().body();
        Assertions.assertTrue(body.length() <= 20, "the rendered body fits maxSize");
        Assertions.assertTrue(body.contains("✂️"), "the cut is marked with scissors");
        Assertions.assertTrue(body.startsWith("0123") && body.endsWith("6789"), "the beginning and the end of the body are kept");
    }

    @Test
    public void theInvocationRedactionsApplyToTheAlert() throws Exception {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer secret-token");
        final var rendering = PayloadsRendering.builder().header("Authorization").jsonPtr("/password").build();

        final var rendered = bodies.abbreviated(invocation(rendering), request(headers, "{\"user\":\"jane\",\"password\":\"secret\"}"), 2048);

        Assertions.assertFalse(rendered.main().headers().getFirst("Authorization").contains("secret-token"), "headers are redacted as configured for the upstream");
        Assertions.assertFalse(rendered.main().body().contains("secret"), "bodies are redacted as configured for the upstream");
        Assertions.assertTrue(rendered.main().body().contains("jane"), "what is not redacted is rendered");
    }

    @Test
    public void multipartRequestsAreRenderedPartByPart() throws Exception {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("multipart/form-data; boundary=b"));
        final var body = String.join("\r\n", List.of(
                "--b",
                "Content-Disposition: form-data; name=\"first\"",
                "",
                "one",
                "--b",
                "Content-Disposition: form-data; name=\"second\"",
                "",
                "two",
                "--b--"));

        final var rendered = bodies.abbreviated(invocation(PayloadsRendering.builder().build()), request(headers, body), 2048);

        Assertions.assertEquals(List.of("one", "two"), rendered.parts().stream().map(PayloadsRendering.RenderedPart::body).toList(), "each part is rendered on its own");
    }

    @Test
    public void longResponseBodiesAreAbbreviatedAroundScissors() throws Exception {
        final var response = new ResponseContext(Instant.now(), HttpStatus.OK, "OK", new HttpHeaders(), BodySource.of("abcdefghij".repeat(10), StandardCharsets.UTF_8), false);

        final var rendered = bodies.abbreviated(invocation(PayloadsRendering.builder().build()), response, 20);

        Assertions.assertTrue(rendered.main().body().length() <= 20, "the rendered body fits maxSize");
        Assertions.assertTrue(rendered.main().body().contains("✂️"), "the cut is marked with scissors");
    }
}
