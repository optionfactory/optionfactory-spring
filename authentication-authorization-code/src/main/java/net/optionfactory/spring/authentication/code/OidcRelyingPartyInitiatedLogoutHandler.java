package net.optionfactory.spring.authentication.code;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

/// Ends the user's session at the identity provider too, by OIDC RP-initiated logout: after the
/// local logout the browser is sent to the provider's end session endpoint, with the id token as
/// `id_token_hint` and the configured `post_logout_redirect_uri`.
///
/// When the logged out authentication is not an OIDC one (a form login, or no authentication at all
/// because the session had already expired) there is no provider session to end, and the browser
/// goes straight to the post logout target.
///
/// The provider must have the post logout target registered for the client, or it will not
/// redirect back.
///
/// ```java
/// http.logout(logout -> logout.logoutSuccessHandler(new OidcRelyingPartyInitiatedLogoutHandler(
///         URI.create("https://idp.example.com/realms/app/protocol/openid-connect/logout"),
///         URI.create("https://app.example.com/"))));
/// ```
public class OidcRelyingPartyInitiatedLogoutHandler implements LogoutSuccessHandler {

    private final URI logoutUri;
    private final URI postLogoutRedirectUri;

    /// @param logoutUri the provider's end session endpoint
    /// @param postLogoutRedirectUri where the browser lands after logout, sent as a single, encoded,
    /// query parameter
    public OidcRelyingPartyInitiatedLogoutHandler(URI logoutUri, URI postLogoutRedirectUri) {
        this.logoutUri = logoutUri;
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

    /// Redirects to the end session endpoint for an OIDC user, to the post logout target otherwise.
    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            final var uri = UriComponentsBuilder
                    .fromUri(logoutUri)
                    .queryParam("id_token_hint", oidcUser.getIdToken().getTokenValue())
                    .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
                    .toUriString();
            response.sendRedirect(response.encodeRedirectURL(uri));
            return;
        }
        response.sendRedirect(postLogoutRedirectUri.toString());
    }

}
