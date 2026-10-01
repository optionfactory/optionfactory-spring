package net.optionfactory.spring.upstream.auth;

import java.time.Clock;
import java.time.Duration;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequest;

/// Authenticates requests with a bearer token obtained via the oauth2
/// client-credentials grant, cached until shortly before its expiry.
public class OauthClientCredentialsAuthenticator implements UpstreamHttpRequestInitializer {

    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final OauthClient client;
    private final OauthAccessTokenCache tokens;

    public OauthClientCredentialsAuthenticator(String clientId, String clientSecret, @Nullable String scope, OauthClient client) {
        this(clientId, clientSecret, scope, client, new OauthAccessTokenCache());
    }

    public OauthClientCredentialsAuthenticator(String clientId, String clientSecret, @Nullable String scope, OauthClient client, OauthAccessTokenCache tokens) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.client = client;
        this.tokens = tokens;
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        final var accessToken = tokens.accessToken(() -> client.clientCredentials(clientId, clientSecret, scope));
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
        private Duration refreshMargin = OauthAccessTokenCache.DEFAULT_REFRESH_MARGIN;
        private Clock clock = Clock.systemUTC();

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

        /// Sets how long before the token's declared `expires_in` a new grant
        /// is executed, absorbing clock skew and in-flight request latency.
        public Builder refreshMargin(Duration refreshMargin) {
            this.refreshMargin = refreshMargin;
            return this;
        }

        /// Sets the clock used for cache expiry.
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public OauthClientCredentialsAuthenticator build() {
            final var tokens = new OauthAccessTokenCache(refreshMargin, OauthAccessTokenCache.DEFAULT_EXPIRES_IN, clock);
            return new OauthClientCredentialsAuthenticator(clientId, clientSecret, scope, client, tokens);
        }
    }
}
