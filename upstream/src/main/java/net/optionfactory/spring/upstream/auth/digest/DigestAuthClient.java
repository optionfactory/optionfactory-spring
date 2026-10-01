package net.optionfactory.spring.upstream.auth.digest;

import java.net.URI;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.errors.RestClientUpstreamException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.service.annotation.PostExchange;

@Upstream("digest-auth-client")
@Upstream.AlertOnRemotingError
public interface DigestAuthClient {

    @PostExchange
    @Upstream.Endpoint("digest-auth-challenge")
    @Upstream.Mock(value = "digest-auth-challenge", status = HttpStatus.UNAUTHORIZED)
    HttpHeaders challenge(URI uri);

    default String authenticate(DigestAuth da, HttpMethod method, URI uri) {
        // RFC 7616: the digest covers the actual method and the request-target, query included
        final var requestUri = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        try {
            final var challenge = challenge(uri).getFirst("WWW-Authenticate");
            return da.authHeader(method.name(), requestUri, challenge);
        } catch (RestClientUpstreamException ex) {
            final var challenge = ex.getResponseHeaders().getFirst("WWW-Authenticate");
            return da.authHeader(method.name(), requestUri, challenge);
        }
    }

    @Deprecated
    default String authenticate(DigestAuth da, URI uri) {
        return authenticate(da, HttpMethod.POST, uri);
    }
}
