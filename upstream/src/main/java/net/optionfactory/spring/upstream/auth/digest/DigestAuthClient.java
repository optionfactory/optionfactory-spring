package net.optionfactory.spring.upstream.auth.digest;

import java.net.URI;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.errors.RestClientUpstreamException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.service.annotation.PostExchange;

/// Obtains the digest challenge of a resource and answers it, for the [DigestAuthenticator].
///
/// Build it with [net.optionfactory.spring.upstream.UpstreamBuilder]; it needs no message converters.
/// It alerts on remoting errors, and its mock answers every challenge request with a `401` carrying a
/// fixed `Digest` challenge (realm `test`).
@Upstream("digest-auth-client")
@Upstream.AlertOnRemotingError
public interface DigestAuthClient {

    /// Posts an empty request to the resource, expecting a `401` carrying the challenge.
    ///
    /// @param uri the resource to authenticate against
    /// @return the response headers when the resource answers with a non error status
    /// @throws net.optionfactory.spring.upstream.errors.RestClientUpstreamException when it answers with
    /// an error status, as the expected `401` does
    @PostExchange
    @Upstream.Endpoint("digest-auth-challenge")
    @Upstream.Mock(value = "digest-auth-challenge", status = HttpStatus.UNAUTHORIZED)
    HttpHeaders challenge(URI uri);

    /// Fetches a fresh challenge from the resource and computes the `Authorization` header answering it.
    ///
    /// As RFC 7616 requires, the digest covers the method of the request being authenticated and its
    /// request-target, the raw path followed by the raw query when there is one. The challenge is read
    /// from the `WWW-Authenticate` header of the `401`, or of a successful response if the resource does
    /// not demand authentication.
    ///
    /// @param da the credentials
    /// @param method the method of the request to authenticate
    /// @param uri the uri of the request to authenticate
    /// @return the `Authorization` header value
    /// @throws IllegalStateException when the response carries no digest challenge
    default String authenticate(DigestAuth da, HttpMethod method, URI uri) {
        final var requestUri = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        try {
            final var challenge = challenge(uri).getFirst("WWW-Authenticate");
            return da.authHeader(method.name(), requestUri, challenge);
        } catch (RestClientUpstreamException ex) {
            final var challenge = ex.getResponseHeaders().getFirst("WWW-Authenticate");
            return da.authHeader(method.name(), requestUri, challenge);
        }
    }

    /// Computes the header for a `POST` to the uri.
    ///
    /// @param da the credentials
    /// @param uri the uri of the request to authenticate
    /// @return the `Authorization` header value
    /// @deprecated the digest must cover the actual method of the request: use
    /// [#authenticate(DigestAuth, HttpMethod, URI)]
    @Deprecated
    default String authenticate(DigestAuth da, URI uri) {
        return authenticate(da, HttpMethod.POST, uri);
    }
}
