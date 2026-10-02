package net.optionfactory.spring.upstream.auth;

import java.time.Clock;
import java.time.Duration;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequest;

/// Authenticates requests with a bearer token obtained via the oauth2 client-credentials grant, cached
/// until shortly before its expiry (see [OauthAccessTokenCache]).
///
/// The client credentials are sent to the token endpoint as `Basic` authentication, as RFC 6749
/// recommends.
///
/// ```java
/// final var oauth = UpstreamBuilder.create(OauthClient.class)
///         .requestFactoryHttpComponents(c -> {})
///         .json(mapper)
///         .baseUri("https://auth.example.com/oauth2/token")
///         .build();
/// final var client = UpstreamBuilder.create(PaymentsClient.class)
///         .initializer(OauthClientCredentialsAuthenticator.builder(oauth).clientId(id).clientSecret(secret).build())
///         ...
/// ```
public class OauthClientCredentialsAuthenticator implements UpstreamHttpRequestInitializer {

    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final OauthClient client;
    private final OauthAccessTokenCache tokens;

    /// Creates an authenticator with a default [OauthAccessTokenCache].
    ///
    /// @param clientId the client id
    /// @param clientSecret the client secret
    /// @param scope the requested scope, or `null` to request none
    /// @param client the token endpoint client
    public OauthClientCredentialsAuthenticator(String clientId, String clientSecret, @Nullable String scope, OauthClient client) {
        this(clientId, clientSecret, scope, client, new OauthAccessTokenCache());
    }

    /// @param clientId the client id
    /// @param clientSecret the client secret
    /// @param scope the requested scope, or `null` to request none
    /// @param client the token endpoint client
    /// @param tokens the token cache, which must not be shared with other grants
    public OauthClientCredentialsAuthenticator(String clientId, String clientSecret, @Nullable String scope, OauthClient client, OauthAccessTokenCache tokens) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.client = client;
        this.tokens = tokens;
    }

    /// Sets the `Authorization: Bearer` header, executing the grant first when no valid token is cached.
    ///
    /// @param invocation the invocation, unused
    /// @param request the request to authenticate
    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        final var accessToken = tokens.accessToken(() -> client.clientCredentials(clientId, clientSecret, scope));
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", accessToken));
    }


    /// @param client the token endpoint client
    /// @return a builder for an authenticator
    public static Builder builder(OauthClient client) {
        return new Builder(client);
    }

    /// Builder for [OauthClientCredentialsAuthenticator].
    public static class Builder {

        private final OauthClient client;
        private String clientId;
        private String clientSecret;
        private String scope;
        private Duration refreshMargin = OauthAccessTokenCache.DEFAULT_REFRESH_MARGIN;
        private Clock clock = Clock.systemUTC();

        /// @param client the token endpoint client
        public Builder(OauthClient client) {
            this.client = client;
        }

        /// @param clientId the client id
        /// @return this builder
        public Builder clientId(@Nullable String clientId) {
            this.clientId = clientId;
            return this;
        }

        /// @param clientSecret the client secret
        /// @return this builder
        public Builder clientSecret(@Nullable String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        /// @param scope the space separated scopes to request, or `null` (the default) to request none
        /// @return this builder
        public Builder scope(@Nullable String scope) {
            this.scope = scope;
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
        public OauthClientCredentialsAuthenticator build() {
            final var tokens = new OauthAccessTokenCache(refreshMargin, OauthAccessTokenCache.DEFAULT_EXPIRES_IN, clock);
            return new OauthClientCredentialsAuthenticator(clientId, clientSecret, scope, client, tokens);
        }
    }
}
