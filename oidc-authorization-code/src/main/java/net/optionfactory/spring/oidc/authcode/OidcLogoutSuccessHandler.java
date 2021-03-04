package net.optionfactory.spring.oidc.authcode;

import java.io.IOException;
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

public class OidcLogoutSuccessHandler implements LogoutSuccessHandler {

    private final URI oidcServerBaseUri;
    private final String path;
    private final boolean useRelativeRedirects;

    public OidcLogoutSuccessHandler(URI oidcServerBaseUri, String path, boolean useRelativeRedirects) {
        this.oidcServerBaseUri = oidcServerBaseUri;
        this.path = path;
        this.useRelativeRedirects = useRelativeRedirects;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        final var builder = UriComponentsBuilder.fromUri(oidcServerBaseUri)
                .path("/logout")
                .queryParam("redirect_uri", UriComponentsBuilder.fromHttpRequest(new ServletServerHttpRequest(request)).replacePath(path).toUriString());

        final var redirectUri = useRelativeRedirects ? builder.scheme(null).host(null).toUriString() : builder.toUriString();
        response.sendRedirect(redirectUri);
    }

}
