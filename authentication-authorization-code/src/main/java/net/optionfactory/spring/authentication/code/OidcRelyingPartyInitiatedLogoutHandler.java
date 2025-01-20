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

public class OidcRelyingPartyInitiatedLogoutHandler implements LogoutSuccessHandler {

    private final URI logoutUri;
    private final URI postLogoutRedirectUri;

    public OidcRelyingPartyInitiatedLogoutHandler(URI logoutUri, URI postLogoutRedirectUri) {
        this.logoutUri = logoutUri;
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

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
        //session is expired, we redirect to the main page
        response.sendRedirect(postLogoutRedirectUri.toString());
    }

}
