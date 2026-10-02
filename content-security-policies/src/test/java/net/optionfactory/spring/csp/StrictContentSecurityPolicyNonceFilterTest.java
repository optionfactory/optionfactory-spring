package net.optionfactory.spring.csp;

import jakarta.servlet.DispatcherType;
import java.util.concurrent.atomic.AtomicBoolean;
import net.optionfactory.spring.csp.StrictContentSecurityPolicyNonceFilter.Csp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.view.RedirectView;

public class StrictContentSecurityPolicyNonceFilterTest {

    private static Csp filtered(MockHttpServletRequest request) throws Exception {
        final var proceeded = new AtomicBoolean(false);
        new StrictContentSecurityPolicyNonceFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> proceeded.set(true));
        Assertions.assertTrue(proceeded.get(), "the request proceeds down the chain");
        return (Csp) request.getAttribute("csp");
    }

    @Test
    public void theNonceIsSixteenRandomBytesHexEncoded() throws Exception {
        final var csp = filtered(new MockHttpServletRequest());

        Assertions.assertNotNull(csp, "the nonce is exposed as the csp request attribute");
        Assertions.assertTrue(csp.nonce().matches("[0-9a-f]{32}"), "the nonce is 16 bytes, hex-encoded, got: " + csp.nonce());
    }

    @Test
    public void everyRequestGetsAFreshNonce() throws Exception {
        final var first = filtered(new MockHttpServletRequest());
        final var second = filtered(new MockHttpServletRequest());

        Assertions.assertNotEquals(first.nonce(), second.nonce(), "a nonce is never reused across requests");
    }

    @Test
    public void anErrorDispatchGetsANonce() throws Exception {
        final var request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.ERROR);

        Assertions.assertNotNull(filtered(request), "an error page rendered by the container still finds a nonce");
    }

    @Test
    public void theNonceIsNotExposedOnRedirects() throws Exception {
        final var view = new RedirectView("/target");
        view.setExposeModelAttributes(true);
        final var response = new MockHttpServletResponse();

        view.render(java.util.Map.of("csp", new Csp("secret-nonce"), "page", "2"), new MockHttpServletRequest(), response);

        Assertions.assertEquals("/target?page=2", response.getRedirectedUrl(), "simple model values are exposed, the csp record is not");
    }
}
