package net.optionfactory.spring.oidc.authcode;

import java.io.IOException;
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
        final var idToken = ((OidcUser) authentication.getPrincipal()).getIdToken().getTokenValue();
        String uri = UriComponentsBuilder
                .fromUri(logoutUri)
                .queryParam("id_token_hint", idToken)
                .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
                .toUriString();
        response.sendRedirect(response.encodeRedirectURL(uri));
    }

}
