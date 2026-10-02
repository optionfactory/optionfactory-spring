package net.optionfactory.spring.upstream.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;

/// Caches and refreshes oauth2 access tokens: the grant executor runs at most once per expiry window,
/// and a token response that does not carry a usable `access_token` fails fast instead of yielding an
/// empty bearer.
///
/// The cache is thread-safe: [#accessToken] is synchronized, so concurrent requests needing a new
/// token wait for a single grant and then share its result. A failed grant caches nothing: the next
/// call executes the grant again.
///
/// A token is retired `refreshMargin` before the `expires_in` declared by the token endpoint,
/// measured from when the grant was started, absorbing clock skew and request latency.
public final class OauthAccessTokenCache {

    /// How long before its declared expiry a token is refreshed, by default.
    public static final Duration DEFAULT_REFRESH_MARGIN = Duration.ofSeconds(60);
    /// The validity assumed when the token endpoint omits a positive `expires_in`: short, because
    /// refreshing too early is harmless while an over-long cache serves expired tokens.
    public static final Duration DEFAULT_EXPIRES_IN = Duration.ofMinutes(1);

    private final Duration refreshMargin;
    private final Duration defaultExpiresIn;
    private final Clock clock;
    private volatile CachedAccessToken cached;

    private record CachedAccessToken(String value, Instant refreshAt) {

    }

    /// Creates a cache with the default refresh margin and expiry, on the system clock.
    public OauthAccessTokenCache() {
        this(DEFAULT_REFRESH_MARGIN, DEFAULT_EXPIRES_IN, Clock.systemUTC());
    }

    /// @param refreshMargin how long before its declared expiry a token is refreshed; a margin longer
    /// than the token validity makes every call execute a grant
    /// @param defaultExpiresIn the validity assumed when the response has no positive `expires_in`
    /// @param clock the clock deciding expiry
    public OauthAccessTokenCache(Duration refreshMargin, Duration defaultExpiresIn, Clock clock) {
        this.refreshMargin = refreshMargin;
        this.defaultExpiresIn = defaultExpiresIn;
        this.clock = clock;
    }

    /// Returns a valid access token, executing the grant when no token is cached or the cached one is
    /// about to expire.
    ///
    /// The grant is not part of the cache key: one cache must serve one grant only.
    ///
    /// @param grant executes the grant against the token endpoint, returning its json response
    /// @return the access token
    /// @throws IllegalStateException when the response carries no `access_token`, or a null or blank
    /// one; whatever the grant throws is propagated as is
    public synchronized String accessToken(Supplier<JsonNode> grant) {
        final var now = clock.instant();
        final var token = cached;
        if (token != null && now.isBefore(token.refreshAt())) {
            return token.value();
        }
        final var response = grant.get();
        final var tokenNode = response.path("access_token");
        if (tokenNode.isMissingNode() || tokenNode.isNull() || tokenNode.asString().isBlank()) {
            throw new IllegalStateException("token response does not carry an access_token");
        }
        final var declaredExpiresIn = response.path("expires_in").asLong(0);
        final var expiresInSeconds = declaredExpiresIn > 0 ? declaredExpiresIn : defaultExpiresIn.toSeconds();
        final var refreshAt = now.plusSeconds(expiresInSeconds).minus(refreshMargin);
        cached = new CachedAccessToken(tokenNode.asString(), refreshAt);
        return cached.value();
    }

}
