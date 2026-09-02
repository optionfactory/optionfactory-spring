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

        Assertions.assertEquals(202, response.getStatus());
        Assertions.assertNull(chain.getRequest(), "the filter chain must not continue for reports");
        Assertions.assertEquals(1, events.size());
        final var event = (ClientError) events.get(0);
        Assertions.assertEquals("the-user", event.principal());
        Assertions.assertEquals("boom", event.content().get("message").asString());
    }

    @Test
    public void reportWithoutAuthenticationCarriesNullPrincipal() throws Exception {
        final var filter = filter("/client-errors/", 65_536, false);

        final var request = new MockHttpServletRequest("POST", "/client-errors/");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        final var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(202, response.getStatus());
        final var event = (ClientError) events.get(0);
        Assertions.assertNull(event.principal());
        Assertions.assertTrue(event.content().isObject());
    }

    @Test
    public void unparseableBodyIsReportedAsTextNode() throws Exception {
        final var filter = filter("/client-errors/", 65_536, false);

        final var request = new MockHttpServletRequest("POST", "/client-errors/");
        request.setContent("not-json".getBytes(StandardCharsets.UTF_8));
        final var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(202, response.getStatus());
        final var event = (ClientError) events.get(0);
        Assertions.assertEquals("unparseable report", event.content().asString());
    }

    @Test
    public void oversizedBodyIsTruncatedAndReportedAsTextNode() throws Exception {
        final var filter = filter("/client-errors/", 8, false);

        final var request = new MockHttpServletRequest("POST", "/client-errors/");
        request.setContent("{\"aLongMessage\":\"0123456789\"}".getBytes(StandardCharsets.UTF_8));
        final var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(202, response.getStatus());
        final var event = (ClientError) events.get(0);
        Assertions.assertEquals("unparseable report", event.content().asString());
    }

    @Test
    public void nonReportRequestsProceedDownTheChain() throws Exception {
        final var filter = filter("/client-errors/", 65_536, false);

        final var request = new MockHttpServletRequest("GET", "/client-errors/");
        final var response = new MockHttpServletResponse();
        final var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        Assertions.assertSame(request, chain.getRequest());
        Assertions.assertTrue(events.isEmpty());

        final var other = new MockHttpServletRequest("POST", "/api/other");
        other.setContent("{}".getBytes(StandardCharsets.UTF_8));
        final var chain2 = new MockFilterChain();
        filter.doFilter(other, new MockHttpServletResponse(), chain2);
        Assertions.assertSame(other, chain2.getRequest());
        Assertions.assertTrue(events.isEmpty());
    }

    @Test
    public void reportIsAcceptedAlsoWhenLoggingIsEnabled() throws Exception {
        final var filter = filter("/client-errors/", 65_536, true);

        final var request = new MockHttpServletRequest("POST", "/client-errors/");
        request.setContent("{\"message\":\"boom\"}".getBytes(StandardCharsets.UTF_8));
        final var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(202, response.getStatus());
        Assertions.assertEquals(1, events.size());
    }
}
