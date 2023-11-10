package net.optionfactory.spring.oidc.authcode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.util.ForwardedHeaderUtils;
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
        final ServletServerHttpRequest sRequest = new ServletServerHttpRequest(request);
        final var builder = UriComponentsBuilder.fromUri(oidcServerBaseUri)
                .path("/logout")
                .queryParam("redirect_uri", ForwardedHeaderUtils
                        .adaptFromForwardedHeaders(sRequest.getURI(), sRequest.getHeaders())
                        .replacePath(path)
                        .toUriString());

        final var redirectUri = useRelativeRedirects ? builder.scheme(null).host(null).toUriString() : builder.toUriString();
        response.sendRedirect(redirectUri);
    }

}
