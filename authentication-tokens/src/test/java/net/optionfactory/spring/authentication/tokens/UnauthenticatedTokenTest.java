package net.optionfactory.spring.authentication.tokens;

import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.UnauthenticatedToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class UnauthenticatedTokenTest {

    private static final HeaderAndScheme BEARER = new HeaderAndScheme("Authorization", "Bearer");
    private static final String SECRET = "super-secret-token";

    private final UnauthenticatedToken token = new UnauthenticatedToken(BEARER, SECRET, new MockHttpServletRequest());

    @Test
    public void itsStringFormDoesNotRevealTheToken() {
        Assertions.assertFalse(token.toString().contains(SECRET), token.toString());
    }

    @Test
    public void itsNameDoesNotRevealTheToken() {
        Assertions.assertFalse(token.getName().contains(SECRET), token.getName());
    }

    @Test
    public void itsPrincipalIsWhereTheTokenWasFound() {
        Assertions.assertEquals(BEARER, token.getPrincipal());
    }

    @Test
    public void theTokenIsStillAvailableAsItsCredentials() {
        Assertions.assertEquals(SECRET, token.getCredentials());
    }
}
