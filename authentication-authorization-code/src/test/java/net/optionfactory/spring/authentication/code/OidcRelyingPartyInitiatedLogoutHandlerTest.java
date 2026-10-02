package net.optionfactory.spring.authentication.code;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

public class OidcRelyingPartyInitiatedLogoutHandlerTest {

    private static final URI END_SESSION = URI.create("https://idp.example.com/logout");
    private static final URI POST_LOGOUT = URI.create("https://app.example.com/goodbye");

    @Test
    public void anOidcUserIsLoggedOutAtTheIdentityProviderWithItsIdToken() throws Exception {
        final var idToken = new OidcIdToken("header.payload.signature", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "alice"));
        final var authentication = new TestingAuthenticationToken(new DefaultOidcUser(List.of(), idToken), null);
        final var response = new MockHttpServletResponse();

        new OidcRelyingPartyInitiatedLogoutHandler(END_SESSION, POST_LOGOUT).onLogoutSuccess(new MockHttpServletRequest(), response, authentication);

        Assertions.assertEquals("https://idp.example.com/logout?id_token_hint=header.payload.signature&post_logout_redirect_uri=https://app.example.com/goodbye", response.getRedirectedUrl(),
                "the browser is sent to the end session endpoint with the id token as hint and the post logout target");
    }

    @Test
    public void aPostLogoutTargetWithAQueryIsEncodedAsASingleParameter() throws Exception {
        final var idToken = new OidcIdToken("header.payload.signature", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "alice"));
        final var authentication = new TestingAuthenticationToken(new DefaultOidcUser(List.of(), idToken), null);
        final var response = new MockHttpServletResponse();

        new OidcRelyingPartyInitiatedLogoutHandler(END_SESSION, URI.create("https://app.example.com/goodbye?a=1&b=2")).onLogoutSuccess(new MockHttpServletRequest(), response, authentication);

        Assertions.assertTrue(response.getRedirectedUrl().endsWith("post_logout_redirect_uri=https://app.example.com/goodbye?a%3D1%26b%3D2"),
                "the target's own query cannot add parameters to the end session request, got: " + response.getRedirectedUrl());
    }

    @Test
    public void withoutAnOidcUserTheBrowserGoesStraightToThePostLogoutTarget() throws Exception {
        final var expired = new MockHttpServletResponse();
        new OidcRelyingPartyInitiatedLogoutHandler(END_SESSION, POST_LOGOUT).onLogoutSuccess(new MockHttpServletRequest(), expired, null);
        Assertions.assertEquals("https://app.example.com/goodbye", expired.getRedirectedUrl(), "an expired session has no id token to end at the identity provider");

        final var otherLogin = new MockHttpServletResponse();
        new OidcRelyingPartyInitiatedLogoutHandler(END_SESSION, POST_LOGOUT).onLogoutSuccess(new MockHttpServletRequest(), otherLogin, new TestingAuthenticationToken("form-user", null));
        Assertions.assertEquals("https://app.example.com/goodbye", otherLogin.getRedirectedUrl(), "a user not logged in through oidc has no identity provider session to end");
    }
}
