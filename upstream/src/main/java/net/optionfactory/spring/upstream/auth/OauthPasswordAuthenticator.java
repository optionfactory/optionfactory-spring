package net.optionfactory.spring.upstream.auth;

import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.jspecify.annotations.Nullable;
import org.springframework.http.client.ClientHttpRequest;

public class OauthPasswordAuthenticator implements UpstreamHttpRequestInitializer {

    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;
    private final OauthClient client;

    public OauthPasswordAuthenticator(@Nullable String clientId, @Nullable String clientSecret, String username, String password, OauthClient client) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
        this.client = client;
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        final var accessToken = client.password(clientId, clientSecret, username, password)
                .get("access_token")
                .asString();
        request.getHeaders().set("Authorization", String.format("Bearer %s", accessToken));
    }

    public static Builder builder(OauthClient client) {
        return new Builder(client);
    }

    public static class Builder {

        private final OauthClient client;
        private String clientId;
        private String clientSecret;
        private String username;
        private String password;

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

        public Builder username(@Nullable String username) {
            this.username = username;
            return this;
        }

        public Builder password(@Nullable String password) {
            this.password = password;
            return this;
        }

        public OauthPasswordAuthenticator build() {
            return new OauthPasswordAuthenticator(clientId, clientSecret, username, password, client);
        }
    }
}
