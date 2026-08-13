package net.optionfactory.spring.authentication.code;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

/// Redirects to the configured Identity Provider's `{@code /logout}` endpoint, passing a
/// `{@code redirect_uri}` query parameter built from the incoming request's scheme, host, and port
/// (as resolved by `ServletServerHttpRequest`, i.e. honoring `Forwarded`/`X-Forwarded-*` headers
/// when a `ForwardedHeaderFilter` is in front of this handler) followed by the configured `path`.
///
/// # Security assumption: the Identity Provider MUST exact-match the redirect target
///
/// The `{@code redirect_uri}` is derived from the request and is therefore not server-controlled:
/// the `Host` header is reflected into it. This is safe **only** under the assumption that the
/// Identity Provider validates the post-logout redirect target by **exact match** (scheme, host,
/// port, and path) against the client's registered redirect URIs, which is the standard OIDC
/// practice. The IdP client registration must enumerate the exact permitted origins.
///
/// Loose, prefix, or wildcard matching at the IdP (or a client registration that omits the exact
/// allowed hosts) would let an attacker spoof the `Host` header and turn logout into an open
/// redirect to an attacker-controlled origin. There is intentionally no app-side allowlist here:
/// validating origins is the IdP's responsibility and a duplicate allowlist would silently drift
/// out of sync. Deployments behind this handler must ensure the IdP matching is exact.
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
                .queryParam("redirect_uri", UriComponentsBuilder.fromUri(sRequest.getURI())
                        .replacePath(path)
                        .toUriString());

        final var redirectUri = useRelativeRedirects ? builder.scheme(null).host(null).toUriString() : builder.toUriString();
        response.sendRedirect(redirectUri);
    }

}
