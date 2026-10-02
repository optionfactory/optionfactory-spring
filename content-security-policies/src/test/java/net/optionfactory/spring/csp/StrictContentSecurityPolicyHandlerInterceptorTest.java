package net.optionfactory.spring.csp;

import net.optionfactory.spring.csp.StrictContentSecurityPolicyNonceFilter.Csp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

public class StrictContentSecurityPolicyHandlerInterceptorTest {

    private static final Csp CSP = new Csp("abc123");

    private static ModelAndView postHandled(ModelAndView mav) throws Exception {
        final var request = new MockHttpServletRequest();
        request.setAttribute("csp", CSP);
        new StrictContentSecurityPolicyHandlerInterceptor().postHandle(request, new MockHttpServletResponse(), null, mav);
        return mav;
    }

    @Test
    public void aRenderedViewGetsTheNonceInItsModel() throws Exception {
        final var mav = postHandled(new ModelAndView("page"));

        Assertions.assertSame(CSP, mav.getModel().get("csp"), "templates find the request's nonce as the csp model attribute");
    }

    @Test
    public void aRedirectViewNameIsLeftAlone() throws Exception {
        final var mav = postHandled(new ModelAndView("redirect:/elsewhere"));

        Assertions.assertFalse(mav.getModel().containsKey("csp"), "a redirect has no page to put the nonce on");
    }

    @Test
    public void aRedirectViewIsLeftAlone() throws Exception {
        final var mav = postHandled(new ModelAndView(new RedirectView("/elsewhere")));

        Assertions.assertFalse(mav.getModel().containsKey("csp"), "a redirecting view has no page to put the nonce on");
    }

    @Test
    public void aHandlerRenderingNoViewIsLeftAlone() {
        Assertions.assertDoesNotThrow(() -> postHandled(null), "a handler writing the response body itself has no model");
    }
}
