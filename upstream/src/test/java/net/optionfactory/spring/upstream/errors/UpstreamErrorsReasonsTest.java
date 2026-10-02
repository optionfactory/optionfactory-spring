package net.optionfactory.spring.upstream.errors;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

public class UpstreamErrorsReasonsTest {

    private static HttpHeaders jsonHeaders(String code) {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Code", code);
        return headers;
    }

    private static UpstreamErrorsReasonsClient client(HttpStatus status, String code) {
        final var headers = jsonHeaders(code);
        return UpstreamBuilder.create(UpstreamErrorsReasonsClient.class)
                .requestFactoryMock(c -> c.response(status, headers, "{\"k\":\"v\"}".getBytes(StandardCharsets.UTF_8)))
                .json(new JsonMapper())
                .baseUri("http://example.com")
                .build();
    }

    @Test
    public void firstMatchingAnnotationProvidesTheTemplatedReason() {
        final var client = client(HttpStatus.OK, "a");
        final var ex = Assertions.assertThrows(RestClientUpstreamException.class, client::firstMatchingWins, "a matching annotation must fail the call");
        Assertions.assertEquals("first: a", ex.reason, "the first matching annotation must provide the reason, rendered as a template");
        Assertions.assertEquals("reasons-upstream", ex.upstream, "the exception must name the upstream");
        Assertions.assertEquals("first-matching", ex.endpoint, "the exception must name the endpoint");
        Assertions.assertEquals("Upstream error for reasons-upstream:first-matching: first: a", ex.getMessage(), "the message must combine upstream, endpoint and reason");
    }

    @Test
    public void laterAnnotationsAreTriedWhenEarlierOnesDoNotMatch() {
        final var client = client(HttpStatus.OK, "b");
        final var ex = Assertions.assertThrows(RestClientUpstreamException.class, client::firstMatchingWins, "the second annotation must match");
        Assertions.assertEquals("second", ex.reason, "the reason must come from the annotation that matched");
    }

    @Test
    public void annotationsOnlyApplyToTheirSeries() {
        Assertions.assertEquals(Map.of("k", "v"), client(HttpStatus.OK, "a").onRedirectsOnly(), "an annotation restricted to redirects must not fire on a 200");
        final var client = client(HttpStatus.MULTIPLE_CHOICES, "a");
        final var ex = Assertions.assertThrows(RestClientUpstreamException.class, client::onRedirectsOnly, "an annotation restricted to redirects must fire on a 3xx");
        Assertions.assertEquals("redirect", ex.reason, "the redirect annotation must provide the reason");
    }

    @Test
    public void reasonCanBeAnExpression() {
        final var client = client(HttpStatus.OK, "a");
        final var ex = Assertions.assertThrows(RestClientUpstreamException.class, client::withExpressionReason, "a matching annotation must fail the call");
        Assertions.assertEquals("computed 200", ex.reason, "an EXPRESSION reason must be evaluated as SpEL");
    }

    @Test
    public void errorStatusesYieldTheStatusAsReason() {
        final var client = client(HttpStatus.NOT_FOUND, "a");
        final var ex = Assertions.assertThrows(RestClientUpstreamException.class, client::plain, "a 4xx must fail the call");
        Assertions.assertEquals("404 Not Found", ex.reason, "the reason must be the status code and text");
        Assertions.assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode(), "the exception must carry the status");
        Assertions.assertEquals("a", ex.getResponseHeaders().getFirst("X-Code"), "the exception must carry the response headers");
        Assertions.assertEquals(Map.of("k", "v"), ex.getResponseBodyAs(Map.class), "the response body must be convertible with the client converters");
    }

    @Test
    public void errorStatusesAreReportedEvenWithoutAnnotations() {
        final var client = client(HttpStatus.BAD_GATEWAY, "a");
        final var ex = Assertions.assertThrows(RestClientUpstreamException.class, client::plain, "a 5xx must fail the call");
        Assertions.assertEquals("plain", ex.endpoint, "an endpoint without @Upstream.Endpoint must be named after its method");
    }

    @Test
    public void clientErrorAnnotationWinsOverTheStatusReason() {
        final var client = client(HttpStatus.NOT_FOUND, "a");
        final var ex = Assertions.assertThrows(RestClientUpstreamException.class, client::onClientErrors, "a matching CLIENT_ERROR annotation must fail the call");
        Assertions.assertEquals("client error: a", ex.reason, "a matching CLIENT_ERROR annotation must provide the reason instead of the status");
        Assertions.assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode(), "the exception must carry the status");
    }

    @Test
    public void clientErrorAnnotationNotMatchingYieldsTheStatusReason() {
        final var client = client(HttpStatus.NOT_FOUND, "b");
        final var ex = Assertions.assertThrows(RestClientUpstreamException.class, client::onClientErrors, "a 4xx must fail the call");
        Assertions.assertEquals("404 Not Found", ex.reason, "a 4xx not matching the annotation must report the status as reason");
    }

    @Test
    public void serverErrorAnnotationWinsOverTheStatusReason() {
        final var client = client(HttpStatus.BAD_GATEWAY, "a");
        final var ex = Assertions.assertThrows(RestClientUpstreamException.class, client::onServerErrors, "a matching SERVER_ERROR annotation must fail the call");
        Assertions.assertEquals("server error", ex.reason, "a matching SERVER_ERROR annotation must provide the reason instead of the status");
    }

    @Test
    public void successfulOnlyAnnotationsDoNotChangeErrorStatusReasons() {
        final var client = client(HttpStatus.NOT_FOUND, "a");
        final var ex = Assertions.assertThrows(RestClientUpstreamException.class, client::firstMatchingWins, "a 4xx must fail the call");
        Assertions.assertEquals("404 Not Found", ex.reason, "annotations for the SUCCESSFUL series only must leave the status as reason of a 4xx");
    }

    @Test
    public void annotationsCanBeRepeatedOnTheInterface() {
        final var client = UpstreamBuilder.create(UpstreamErrorsRepeatedOnTypeClient.class)
                .requestFactoryMock(c -> c.response(HttpStatus.OK, jsonHeaders("b"), "{\"k\":\"v\"}".getBytes(StandardCharsets.UTF_8)))
                .json(new JsonMapper())
                .baseUri("http://example.com")
                .build();
        final var ex = Assertions.assertThrows(RestClientUpstreamException.class, client::inherited, "the annotations repeated on the interface must apply to its methods");
        Assertions.assertEquals("type second", ex.reason, "the repeated interface annotations must be tried in declaration order");
    }
}
