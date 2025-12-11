package net.optionfactory.spring.upstream.auth;

import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequest;

public class OauthClientCredentialsAuthenticator implements UpstreamHttpRequestInitializer {

    private final String clientId;
    private final String clientSecret;
    private final OauthClient client;

    public OauthClientCredentialsAuthenticator(String clientId, String clientSecret, OauthClient client) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.client = client;
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        final var accessToken = client.clientCredentials(clientId, clientSecret)
                .get("access_token")
                .asString();
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", accessToken));
    }
}
