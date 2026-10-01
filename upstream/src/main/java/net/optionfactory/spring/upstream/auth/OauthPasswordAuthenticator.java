package net.optionfactory.spring.upstream.auth;

import java.time.Clock;
import java.time.Duration;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequest;

/// Authenticates requests with a bearer token obtained via the oauth2
/// resource-owner password grant, cached until shortly before its expiry.
public class OauthPasswordAuthenticator implements UpstreamHttpRequestInitializer {

    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;
    private final OauthClient client;
    private final OauthAccessTokenCache tokens;

    public OauthPasswordAuthenticator(@Nullable String clientId, @Nullable String clientSecret, String username, String password, OauthClient client) {
        this(clientId, clientSecret, username, password, client, new OauthAccessTokenCache());
    }

    public OauthPasswordAuthenticator(@Nullable String clientId, @Nullable String clientSecret, String username, String password, OauthClient client, OauthAccessTokenCache tokens) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
        this.client = client;
        this.tokens = tokens;
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        final var accessToken = tokens.accessToken(() -> client.password(clientId, clientSecret, username, password));
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", accessToken));
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

        public Builder username(@Nullable String username) {
            this.username = username;
            return this;
        }

        public Builder password(@Nullable String password) {
            this.password = password;
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

        public OauthPasswordAuthenticator build() {
            final var tokens = new OauthAccessTokenCache(refreshMargin, OauthAccessTokenCache.DEFAULT_EXPIRES_IN, clock);
            return new OauthPasswordAuthenticator(clientId, clientSecret, username, password, client, tokens);
        }
    }
}
