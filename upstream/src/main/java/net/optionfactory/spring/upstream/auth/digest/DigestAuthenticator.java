package net.optionfactory.spring.upstream.auth.digest;

import java.io.IOException;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;

/// Authenticates requests with HTTP digest authentication (`qop=auth`, `MD5`).
///
/// An interceptor, registered with [net.optionfactory.spring.upstream.UpstreamBuilder#interceptor]:
/// it runs after the `@Upstream.QueryParam`, `Header` and `Cookie` values are applied, so the uri
/// digested is the one sent.
///
/// Nothing is cached: every request is preceded by a challenge request to the same uri through the
/// [DigestAuthClient], so each authenticated call costs two exchanges.
public class DigestAuthenticator implements UpstreamHttpInterceptor {

    private final DigestAuth digestAuth;
    private final DigestAuthClient client;

    /// @param clientId the username
    /// @param clientSecret the password
    /// @param client the client fetching the challenges
    public DigestAuthenticator(String clientId, String clientSecret, DigestAuthClient client) {
        this.digestAuth = DigestAuth.fromCredentials(clientId, clientSecret);
        this.client = client;
    }

    /// Sets the `Authorization` header answering the digest challenge of the request's uri, then
    /// proceeds.
    ///
    /// @param invocation the invocation
    /// @param request the request to authenticate
    /// @param execution the rest of the chain
    /// @return the response
    /// @throws IOException when the exchange fails
    @Override
    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        final var header = client.authenticate(digestAuth, request.method(), request.uri());
        request.headers().set("Authorization", header);
        return execution.execute(invocation, request);
    }
}
