package net.optionfactory.spring.upstream.auth.digest;

import java.net.URI;
import net.optionfactory.spring.upstream.Upstream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.service.annotation.PostExchange;

@Upstream("digest-auth-client")
@Upstream.AlertOnRemotingError
public interface DigestAuthClient {

    @PostExchange
    @Upstream.Endpoint("digest-auth-challenge")
    @Upstream.Mock(value = "digest-auth-challenge", status = HttpStatus.UNAUTHORIZED)
    HttpHeaders challenge(URI uri);

    default String authenticate(DigestAuth da, URI uri) {
        try {
            final var challenge = challenge(uri).getFirst("WWW-Authenticate");
            return da.authHeader("POST", uri.getPath(), challenge);
        } catch (HttpClientErrorException.Unauthorized ex) {
            final var challenge = ex.getResponseHeaders().getFirst("WWW-Authenticate");
            return da.authHeader("POST", uri.getPath(), challenge);
        }
    }
}
