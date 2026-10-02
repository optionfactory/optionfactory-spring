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

/// Redirects to the configured Identity Provider's `/logout` endpoint, passing a
/// `redirect_uri` query parameter built from the incoming request's scheme, host, and port
/// (as resolved by `ServletServerHttpRequest`, i.e. honoring `Forwarded`/`X-Forwarded-*` headers
/// when a `ForwardedHeaderFilter` is in front of this handler) followed by the configured `path`.
/// The incoming request's query string is never carried into the redirect target.
///
/// # Security assumption: the Identity Provider MUST exact-match the redirect target
///
/// The `redirect_uri` is derived from the request and is therefore not server-controlled:
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
///
/// The incoming query string is dropped because the IdP's exact match would reject it, and because
/// an attacker could otherwise smuggle it onto the post-logout landing page.
///
/// Unlike [OidcRelyingPartyInitiatedLogoutHandler], no `id_token_hint` is sent, and the parameter is
/// named `redirect_uri` rather than OIDC's `post_logout_redirect_uri`: this handler targets providers
/// whose `/logout` endpoint expects that.
public class OidcLogoutSuccessHandler implements LogoutSuccessHandler {

    private final URI oidcServerBaseUri;
    private final String path;
    private final boolean useRelativeRedirects;

    /// @param oidcServerBaseUri the provider's base uri, `/logout` is appended to its path
    /// @param path the application path the browser lands on after logout
    /// @param useRelativeRedirects true to redirect to the provider's path only, dropping its scheme,
    /// host and port, for a provider served from the application's own origin (e.g. behind the same
    /// reverse proxy)
    public OidcLogoutSuccessHandler(URI oidcServerBaseUri, String path, boolean useRelativeRedirects) {
        this.oidcServerBaseUri = oidcServerBaseUri;
        this.path = path;
        this.useRelativeRedirects = useRelativeRedirects;
    }

    /// Redirects to the provider's `/logout`, whatever the logged out authentication.
    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        final ServletServerHttpRequest sRequest = new ServletServerHttpRequest(request);
        final var builder = UriComponentsBuilder.fromUri(oidcServerBaseUri)
                .path("/logout")
                .queryParam("redirect_uri", UriComponentsBuilder.fromUri(sRequest.getURI())
                        .replacePath(path)
                        .replaceQuery(null)
                        .toUriString());

        final var redirectUri = useRelativeRedirects ? builder.scheme(null).host(null).toUriString() : builder.toUriString();
        response.sendRedirect(redirectUri);
    }

}
