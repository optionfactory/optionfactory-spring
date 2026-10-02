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
        Assertions.assertFalse(token.toString().contains(SECRET), "the string form, which gets logged, must not reveal the token");
    }

    @Test
    public void itsNameDoesNotRevealTheToken() {
        Assertions.assertFalse(token.getName().contains(SECRET), "the name, which gets logged and audited, must not reveal the token");
    }

    @Test
    public void itsPrincipalIsWhereTheTokenWasFound() {
        Assertions.assertEquals(BEARER, token.getPrincipal(), "the principal is the header and scheme the token was found on");
    }

    @Test
    public void theTokenIsStillAvailableAsItsCredentials() {
        Assertions.assertEquals(SECRET, token.getCredentials(), "the token is still available as the credentials");
    }
}
