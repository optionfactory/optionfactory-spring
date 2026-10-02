package net.optionfactory.spring.upstream.auth.digest;

import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.client.ClientHttpRequest;

/// Authenticates requests with HTTP digest authentication (`qop=auth`, `MD5`).
///
/// Nothing is cached: every request is preceded by a challenge request to the same uri through the
/// [DigestAuthClient], so each authenticated call costs two exchanges. The uri digested is the one the
/// request has when initializers run, before the interceptors adding the `@Upstream.QueryParam`
/// values.
public class DigestAuthenticator implements UpstreamHttpRequestInitializer {

    private final DigestAuth digestAuth;
    private final DigestAuthClient client;

    /// @param clientId the username
    /// @param clientSecret the password
    /// @param client the client fetching the challenges
    public DigestAuthenticator(String clientId, String clientSecret, DigestAuthClient client) {
        this.digestAuth = DigestAuth.fromCredentials(clientId, clientSecret);
        this.client = client;
    }

    /// @param invocation the invocation, unused
    /// @param request the request to authenticate
    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        final var header = client.authenticate(digestAuth, request.getMethod(), request.getURI());
        request.getHeaders().set("Authorization", header);
    }

}
