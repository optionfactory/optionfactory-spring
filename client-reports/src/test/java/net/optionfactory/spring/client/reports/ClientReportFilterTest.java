package net.optionfactory.spring.client.reports;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;

public class ClientReportFilterTest {

    public record ClientError(Object principal, JsonNode content) {

    }

    private final List<Object> events = new ArrayList<>();

    @AfterEach
    public void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private ClientReportFilter<ClientError> filter(String reportUri, int maxBodySize, boolean log) {
        return new ClientReportFilter<>("client-error", reportUri, events::add, maxBodySize, log, ClientError::new, p -> String.format("[user:%s]", p));
    }

    @Test
    public void reportIsPublishedWithAuthenticatedPrincipalAndAccepted() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("the-user", "credentials", "ROLE_USER"));
        final var filter = filter("/client-errors/", 65_536, false);

        final var request = new MockHttpServletRequest("POST", "/client-errors/");
        request.setContent("{\"message\":\"boom\"}".getBytes(StandardCharsets.UTF_8));
        final var response = new MockHttpServletResponse();
        final var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Assertions.assertEquals(202, response.getStatus(), "a report is acknowledged with 202 Accepted");
        Assertions.assertNull(chain.getRequest(), "the filter chain must not continue for reports");
        Assertions.assertEquals(1, events.size(), "one event per report");
        final var event = (ClientError) events.get(0);
        Assertions.assertEquals("the-user", event.principal(), "the event carries the authenticated principal");
        Assertions.assertEquals("boom", event.content().get("message").asString(), "the event carries the parsed report");
    }

    @Test
    public void reportWithoutAuthenticationCarriesNullPrincipal() throws Exception {
        final var filter = filter("/client-errors/", 65_536, false);

        final var request = new MockHttpServletRequest("POST", "/client-errors/");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        final var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(202, response.getStatus(), "anonymous reports are accepted");
        final var event = (ClientError) events.get(0);
        Assertions.assertNull(event.principal(), "an anonymous report carries a null principal");
        Assertions.assertTrue(event.content().isObject(), "the report is parsed as json");
    }

    @Test
    public void unparseableBodyIsReportedAsTextNode() throws Exception {
        final var filter = filter("/client-errors/", 65_536, false);

        final var request = new MockHttpServletRequest("POST", "/client-errors/");
        request.setContent("not-json".getBytes(StandardCharsets.UTF_8));
        final var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(202, response.getStatus(), "an unparseable report is still accepted");
        final var event = (ClientError) events.get(0);
        Assertions.assertEquals("unparseable report", event.content().asString(), "an unparseable body is replaced by a text node");
    }

    @Test
    public void oversizedBodyIsTruncatedAndReportedAsTextNode() throws Exception {
        final var filter = filter("/client-errors/", 8, false);

        final var request = new MockHttpServletRequest("POST", "/client-errors/");
        request.setContent("{\"aLongMessage\":\"0123456789\"}".getBytes(StandardCharsets.UTF_8));
        final var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(202, response.getStatus(), "an oversized report is still accepted");
        final var event = (ClientError) events.get(0);
        Assertions.assertEquals("unparseable report", event.content().asString(), "a body truncated at maxBodySize no longer parses");
    }

    @Test
    public void bodyExactlyAtTheLimitIsParsed() throws Exception {
        final var filter = filter("/client-errors/", 2, false);

        final var request = new MockHttpServletRequest("POST", "/client-errors/");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        final var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(202, response.getStatus(), "a report at the limit is accepted");
        final var event = (ClientError) events.get(0);
        Assertions.assertTrue(event.content().isObject(), "a body of exactly maxBodySize bytes must be accepted");
    }

    @Test
    public void reportUnderAContextPathIsAccepted() throws Exception {
        final var filter = filter("/client-errors/", 65_536, false);

        final var request = new MockHttpServletRequest("POST", "/client-errors/");
        request.setContextPath("/app");
        request.setRequestURI("/app/client-errors/");
        request.setContent("{\"message\":\"boom\"}".getBytes(StandardCharsets.UTF_8));
        final var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(202, response.getStatus(), "a report under a context path is accepted");
        Assertions.assertEquals(1, events.size(), "a report posted under a context path must be received, not silently dropped");
    }

    @Test
    public void nonReportRequestsProceedDownTheChain() throws Exception {
        final var filter = filter("/client-errors/", 65_536, false);

        final var request = new MockHttpServletRequest("GET", "/client-errors/");
        final var response = new MockHttpServletResponse();
        final var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        Assertions.assertSame(request, chain.getRequest(), "a GET on the report uri proceeds down the chain");
        Assertions.assertTrue(events.isEmpty(), "a GET on the report uri publishes nothing");

        final var other = new MockHttpServletRequest("POST", "/api/other");
        other.setContent("{}".getBytes(StandardCharsets.UTF_8));
        final var chain2 = new MockFilterChain();
        filter.doFilter(other, new MockHttpServletResponse(), chain2);
        Assertions.assertSame(other, chain2.getRequest(), "a POST elsewhere proceeds down the chain");
        Assertions.assertTrue(events.isEmpty(), "a POST elsewhere publishes nothing");
    }

    @Test
    public void reportIsAcceptedAlsoWhenLoggingIsEnabled() throws Exception {
        final var filter = filter("/client-errors/", 65_536, true);

        final var request = new MockHttpServletRequest("POST", "/client-errors/");
        request.setContent("{\"message\":\"boom\"}".getBytes(StandardCharsets.UTF_8));
        final var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(202, response.getStatus(), "logging does not change the response");
        Assertions.assertEquals(1, events.size(), "logging does not change the publishing");
    }

    @Test
    public void theUriMustMatchExactly() throws Exception {
        final var filter = filter("/client-errors/", 65_536, false);

        final var request = new MockHttpServletRequest("POST", "/client-errors");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        final var chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Assertions.assertSame(request, chain.getRequest(), "a uri differing by the trailing slash is not a report");
        Assertions.assertTrue(events.isEmpty(), "a uri differing by the trailing slash publishes nothing");
    }

    @Test
    public void thePrincipalRendererIsOnlyUsedForLogging() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("the-user", "credentials", "ROLE_USER"));
        final var rendered = new ArrayList<Object>();
        final var silent = new ClientReportFilter<>("client-error", "/client-errors/", events::add, 65_536, false, ClientError::new, p -> {
            rendered.add(p);
            return "";
        });
        final var logging = new ClientReportFilter<>("client-error", "/client-errors/", events::add, 65_536, true, ClientError::new, p -> {
            rendered.add(p);
            return "";
        });

        silent.doFilter(report("{}"), new MockHttpServletResponse(), new MockFilterChain());
        Assertions.assertTrue(rendered.isEmpty(), "the principal is not rendered when logging is disabled");
        logging.doFilter(report("{}"), new MockHttpServletResponse(), new MockFilterChain());
        Assertions.assertEquals(List.of("the-user"), rendered, "the principal is rendered for the log line when logging is enabled");
    }

    @Test
    public void anEmptyBodyIsReportedAsUnparseable() throws Exception {
        final var filter = filter("/client-errors/", 65_536, false);

        filter.doFilter(new MockHttpServletRequest("POST", "/client-errors/"), new MockHttpServletResponse(), new MockFilterChain());

        final var event = (ClientError) events.get(0);
        Assertions.assertEquals("unparseable report", event.content().asString(), "a report without body is replaced by a text node");
    }

    private static MockHttpServletRequest report(String json) {
        final var request = new MockHttpServletRequest("POST", "/client-errors/");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
