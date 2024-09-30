package net.optionfactory.spring.upstream.auth;

import java.net.URI;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.client.ClientHttpRequest;

public class OauthPasswordAuthenticator implements UpstreamHttpRequestInitializer {

    private final String clientId;
    private final String username;
    private final String password;
    private final OauthClient client;

    public OauthPasswordAuthenticator(String clientId, String username, String password, URI tokenUri, OauthClient client) {
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.client = client;
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        final var accessToken = client.password(clientId, username, password)
                .get("access_token")
                .asText();
        request.getHeaders().set("Authorization", String.format("Bearer %s", accessToken));
    }

}
