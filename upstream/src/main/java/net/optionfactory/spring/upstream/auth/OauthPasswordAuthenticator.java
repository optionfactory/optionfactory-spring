package net.optionfactory.spring.upstream.auth;

import java.time.Clock;
import java.time.Duration;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequest;

/// Authenticates requests with a bearer token obtained via the oauth2 resource-owner password grant,
/// cached until shortly before its expiry (see [OauthAccessTokenCache]).
///
/// All the credentials, the client ones included when given, are sent as form parameters.
public class OauthPasswordAuthenticator implements UpstreamHttpRequestInitializer {

    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;
    private final OauthClient client;
    private final OauthAccessTokenCache tokens;

    /// Creates an authenticator with a default [OauthAccessTokenCache].
    ///
    /// @param clientId the client id, or `null` not to send one
    /// @param clientSecret the client secret, or `null` not to send one
    /// @param username the resource owner username
    /// @param password the resource owner password
    /// @param client the token endpoint client
    public OauthPasswordAuthenticator(@Nullable String clientId, @Nullable String clientSecret, String username, String password, OauthClient client) {
        this(clientId, clientSecret, username, password, client, new OauthAccessTokenCache());
    }

    /// @param clientId the client id, or `null` not to send one
    /// @param clientSecret the client secret, or `null` not to send one
    /// @param username the resource owner username
    /// @param password the resource owner password
    /// @param client the token endpoint client
    /// @param tokens the token cache, which must not be shared with other grants
    public OauthPasswordAuthenticator(@Nullable String clientId, @Nullable String clientSecret, String username, String password, OauthClient client, OauthAccessTokenCache tokens) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
        this.client = client;
        this.tokens = tokens;
    }

    /// Sets the `Authorization: Bearer` header, executing the grant first when no valid token is cached.
    ///
    /// @param invocation the invocation, unused
    /// @param request the request to authenticate
    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        final var accessToken = tokens.accessToken(() -> client.password(clientId, clientSecret, username, password));
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", accessToken));
    }

    /// @param client the token endpoint client
    /// @return a builder for an authenticator
    public static Builder builder(OauthClient client) {
        return new Builder(client);
    }

    /// Builder for [OauthPasswordAuthenticator].
    public static class Builder {

        private final OauthClient client;
        private String clientId;
        private String clientSecret;
        private String username;
        private String password;
        private Duration refreshMargin = OauthAccessTokenCache.DEFAULT_REFRESH_MARGIN;
        private Clock clock = Clock.systemUTC();

        /// @param client the token endpoint client
        public Builder(OauthClient client) {
            this.client = client;
        }

        /// @param clientId the client id, or `null` (the default) not to send one
        /// @return this builder
        public Builder clientId(@Nullable String clientId) {
            this.clientId = clientId;
            return this;
        }

        /// @param clientSecret the client secret, or `null` (the default) not to send one
        /// @return this builder
        public Builder clientSecret(@Nullable String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        /// @param username the resource owner username
        /// @return this builder
        public Builder username(@Nullable String username) {
            this.username = username;
            return this;
        }

        /// @param password the resource owner password
        /// @return this builder
        public Builder password(@Nullable String password) {
            this.password = password;
            return this;
        }

        /// Sets how long before the token's declared `expires_in` a new grant is executed, absorbing clock
        /// skew and in-flight request latency; defaults to [OauthAccessTokenCache#DEFAULT_REFRESH_MARGIN].
        ///
        /// @param refreshMargin the margin
        /// @return this builder
        public Builder refreshMargin(Duration refreshMargin) {
            this.refreshMargin = refreshMargin;
            return this;
        }

        /// Sets the clock used for cache expiry; defaults to the system clock.
        ///
        /// @param clock the clock
        /// @return this builder
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /// @return a new authenticator, with its own token cache
        public OauthPasswordAuthenticator build() {
            final var tokens = new OauthAccessTokenCache(refreshMargin, OauthAccessTokenCache.DEFAULT_EXPIRES_IN, clock);
            return new OauthPasswordAuthenticator(clientId, clientSecret, username, password, client, tokens);
        }
    }
}
