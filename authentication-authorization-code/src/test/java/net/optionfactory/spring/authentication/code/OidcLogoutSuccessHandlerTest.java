package net.optionfactory.spring.authentication.code;

import java.net.URI;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class OidcLogoutSuccessHandlerTest {

    @Test
    public void redirectUriIgnoresPoisonedForwardedHost() throws Exception {
        final var handler = new OidcLogoutSuccessHandler(URI.create("https://idp.example.com"), "/app/home", false);
        final var req = new MockHttpServletRequest();
        req.setScheme("https");
        req.setServerName("app.example.com");
        req.setServerPort(-1);
        req.setRequestURI("/app/home");
        req.addHeader("X-Forwarded-Host", "attacker.example");

        final var res = new MockHttpServletResponse();
        handler.onLogoutSuccess(req, res, null);

        final var redirectedUrl = res.getRedirectedUrl();
        Assertions.assertNotNull(redirectedUrl);
        Assertions.assertFalse(redirectedUrl.contains("attacker"),
                "redirect_uri must not honor a client-supplied X-Forwarded-Host");
        Assertions.assertTrue(redirectedUrl.contains("app.example.com"),
                "redirect_uri must reflect the request's own host");
    }
}
