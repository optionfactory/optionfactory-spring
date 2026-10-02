package net.optionfactory.spring.authentication;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

public class UnauthorizedStatusEntryPointTest {

    private static MockHttpServletResponse commence(UnauthorizedStatusEntryPoint entryPoint) throws Exception {
        final var response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("unauthenticated"));
        return response;
    }

    @Test
    public void aBearerChallengeAnnouncesTheBearerScheme() throws Exception {
        final var response = commence(UnauthorizedStatusEntryPoint.bearerChallenge());

        Assertions.assertEquals(401, response.getStatus(), "an unauthenticated request is answered with 401");
        Assertions.assertEquals("Bearer", response.getHeader("WWW-Authenticate"), "the challenge names the bearer scheme");
        Assertions.assertNull(response.getRedirectedUrl(), "no redirect to a login page");
    }

    @Test
    public void aCustomChallengeIsWrittenVerbatim() throws Exception {
        final var response = commence(UnauthorizedStatusEntryPoint.authScheme("Basic realm=\"api\""));

        Assertions.assertEquals(401, response.getStatus(), "an unauthenticated request is answered with 401");
        Assertions.assertEquals("Basic realm=\"api\"", response.getHeader("WWW-Authenticate"), "the configured challenge is written as given");
    }

    @Test
    public void noChallengeSetsTheStatusOnly() throws Exception {
        final var response = commence(UnauthorizedStatusEntryPoint.noChallenge());

        Assertions.assertEquals(401, response.getStatus(), "an unauthenticated request is answered with 401");
        Assertions.assertFalse(response.containsHeader("WWW-Authenticate"), "no challenge header when none is configured");
    }
}
