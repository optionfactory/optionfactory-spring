package net.optionfactory.spring.csp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.optionfactory.spring.csp.StrictContentSecurityPolicyReportFilter.CspViolation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public class StrictContentSecurityPolicyReportFilterTest {

    private final List<Object> events = new ArrayList<>();
    private final StrictContentSecurityPolicyReportFilter filter = new StrictContentSecurityPolicyReportFilter("/csp-violations/", events::add, 1024, false, p -> String.valueOf(p));

    @AfterEach
    public void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest report(String body) {
        final var request = new MockHttpServletRequest("POST", "/app/csp-violations/");
        request.setContextPath("/app");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    public void aReportIsAcceptedAndPublished() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));
        final var response = new MockHttpServletResponse();
        final var proceeded = new AtomicBoolean(false);

        filter.doFilter(report("{\"type\": \"csp-violation\"}"), response, (req, res) -> proceeded.set(true));

        Assertions.assertEquals(202, response.getStatus(), "a report is answered with 202 Accepted");
        Assertions.assertFalse(proceeded.get(), "a report is consumed by the filter, not passed down the chain");
        Assertions.assertEquals(1, events.size(), "one event per report");
        final var violation = Assertions.assertInstanceOf(CspViolation.class, events.get(0), "the event is a CspViolation");
        Assertions.assertEquals("alice", violation.principal(), "the event carries the reporting request's principal");
        Assertions.assertEquals("csp-violation", violation.content().get("type").asString(), "the event carries the parsed report");
    }

    @Test
    public void anUnparseableReportIsStillAccepted() throws Exception {
        final var response = new MockHttpServletResponse();

        filter.doFilter(report("not json"), response, (req, res) -> {
        });

        Assertions.assertEquals(202, response.getStatus(), "a malformed report never fails the request");
        final var violation = (CspViolation) events.get(0);
        Assertions.assertNull(violation.principal(), "an unauthenticated report has no principal");
        Assertions.assertEquals("unparseable report", violation.content().asString(), "a malformed report is recorded as unparseable");
    }

    @Test
    public void otherRequestsProceed() throws Exception {
        final var proceeded = new AtomicBoolean(false);
        final var get = new MockHttpServletRequest("GET", "/csp-violations/");

        filter.doFilter(get, new MockHttpServletResponse(), (req, res) -> proceeded.set(true));

        Assertions.assertTrue(proceeded.get(), "only a POST to the report uri is a report");
        Assertions.assertTrue(events.isEmpty(), "no event is published for other requests");
    }
}
