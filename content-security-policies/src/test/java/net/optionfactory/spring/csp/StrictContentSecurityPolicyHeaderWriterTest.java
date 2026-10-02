package net.optionfactory.spring.csp;

import net.optionfactory.spring.csp.StrictContentSecurityPolicyHeaderWriter.ContentSecurityPolicyMode;
import net.optionfactory.spring.csp.StrictContentSecurityPolicyNonceFilter.Csp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class StrictContentSecurityPolicyHeaderWriterTest {

    private static MockHttpServletRequest withNonce(String nonce) {
        final var request = new MockHttpServletRequest();
        request.setAttribute("csp", new Csp(nonce));
        return request;
    }

    private static MockHttpServletResponse write(StrictContentSecurityPolicyHeaderWriter writer, MockHttpServletRequest request) {
        final var response = new MockHttpServletResponse();
        writer.writeHeaders(request, response);
        return response;
    }

    @Test
    public void theStrictPolicyCarriesTheRequestsNonce() {
        final var response = write(new StrictContentSecurityPolicyHeaderWriter(ContentSecurityPolicyMode.ENFORCE, "/csp-violations/", false, false), withNonce("abc123"));

        Assertions.assertEquals("object-src 'none';script-src 'nonce-abc123' 'strict-dynamic';base-uri 'self';report-uri /csp-violations/", response.getHeader("Content-Security-Policy"),
                "without eval nor fallbacks the policy allows only nonced scripts and what they load");
    }

    @Test
    public void evalAndFallbacksAreAddedWhenEnabled() {
        final var response = write(new StrictContentSecurityPolicyHeaderWriter(ContentSecurityPolicyMode.ENFORCE, "/r", true, true), withNonce("abc123"));

        Assertions.assertEquals("object-src 'none';script-src 'nonce-abc123' 'strict-dynamic' 'unsafe-eval' 'unsafe-inline' https:;base-uri 'self';report-uri /r", response.getHeader("Content-Security-Policy"),
                "eval and the fallbacks for browsers without nonce support are appended to script-src");
    }

    @Test
    public void theReportModeUsesTheReportOnlyHeader() {
        final var response = write(new StrictContentSecurityPolicyHeaderWriter(ContentSecurityPolicyMode.REPORT, "/r", false, false), withNonce("abc123"));

        Assertions.assertNotNull(response.getHeader("Content-Security-Policy-Report-Only"), "a reported policy is sent as Content-Security-Policy-Report-Only");
        Assertions.assertNull(response.getHeader("Content-Security-Policy"), "a reported policy is not enforced");
    }

    @Test
    public void theDisabledModeWritesNothingEvenWithoutANonce() {
        final var response = write(new StrictContentSecurityPolicyHeaderWriter(ContentSecurityPolicyMode.DISABLE, "/r", false, false), new MockHttpServletRequest());

        Assertions.assertTrue(response.getHeaderNames().isEmpty(), "a disabled policy writes no header and needs no nonce");
    }

    @Test
    public void aMissingNonceIsAMisconfiguration() {
        final var writer = new StrictContentSecurityPolicyHeaderWriter(ContentSecurityPolicyMode.ENFORCE, "/r", false, false);

        Assertions.assertThrows(IllegalStateException.class, () -> write(writer, new MockHttpServletRequest()), "a policy without the nonce filter in front would block every script");
    }

    @Test
    public void eachRequestGetsItsOwnNonce() {
        final var writer = new StrictContentSecurityPolicyHeaderWriter(ContentSecurityPolicyMode.ENFORCE, "/r", false, false);

        final var first = write(writer, withNonce("first")).getHeader("Content-Security-Policy");
        final var second = write(writer, withNonce("second")).getHeader("Content-Security-Policy");

        Assertions.assertTrue(first.contains("'nonce-first'"), "the first request's policy carries its nonce");
        Assertions.assertTrue(second.contains("'nonce-second'") && !second.contains("first"), "a writer shared by requests renders each with its own nonce");
    }
}
