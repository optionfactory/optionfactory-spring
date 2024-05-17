package net.optionfactory.spring.upstream.auth.digest;

import java.net.URI;
import net.optionfactory.spring.upstream.Upstream;
import static net.optionfactory.spring.upstream.Upstream.FaultOnResponse.STATUS_IS_ERROR;
import org.springframework.http.HttpHeaders;
import org.springframework.web.service.annotation.PostExchange;

@Upstream("digest-auth-client")
@Upstream.FaultOnRemotingError
@Upstream.FaultOnResponse(STATUS_IS_ERROR)
public interface DigestAuthClient {

    @PostExchange
    @Upstream.Endpoint("digest-auth-challenge")
    @Upstream.Mock("digest-auth-challenge")
    HttpHeaders challenge(URI uri);

    default String authenticate(DigestAuth da, URI uri) {
        final var challenge = challenge(uri).getFirst("WWW-Authenticate");
        return da.authHeader("POST", uri.getPath(), challenge);
    }
}
