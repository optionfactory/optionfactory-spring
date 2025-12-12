package net.optionfactory.spring.upstream.auth;

import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequest;

public class OauthClientCredentialsAuthenticator implements UpstreamHttpRequestInitializer {

    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final OauthClient client;

    public OauthClientCredentialsAuthenticator(String clientId, String clientSecret, @Nullable String scope, OauthClient client) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.client = client;
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        final var accessToken = client.clientCredentials(clientId, clientSecret, scope)
                .get("access_token")
                .asString();
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", accessToken));
    }
    

    public static Builder builder(OauthClient client) {
        return new Builder(client);
    }

    public static class Builder {

        private final OauthClient client;
        private String clientId;
        private String clientSecret;
        private String scope;

        public Builder(OauthClient client) {
            this.client = client;
        }

        public Builder clientId(@Nullable String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientSecret(@Nullable String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder scope(@Nullable String scope) {
            this.scope = scope;
            return this;
        }

        public OauthClientCredentialsAuthenticator build() {
            return new OauthClientCredentialsAuthenticator(clientId, clientSecret, scope, client);
        }
    }    
}
