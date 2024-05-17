package net.optionfactory.spring.upstream.auth.digest;

import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.client.ClientHttpRequest;

public class DigestAuthenticator implements UpstreamHttpRequestInitializer {

    private final DigestAuth digestAuth;
    private final DigestAuthClient client;

    public DigestAuthenticator(String clientId, String clientSecret, DigestAuthClient client) {
        this.digestAuth = DigestAuth.fromCredentials(clientId, clientSecret);
        this.client = client;
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        final var header = client.authenticate(digestAuth, request.getURI());
        request.getHeaders().set("Authorization", header);
    }

}
