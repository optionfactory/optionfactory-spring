package net.optionfactory.spring.upstream.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;

/// Caches and refreshes oauth2 access tokens: the grant executor runs at most
/// once per expiry window, and a token response that does not carry a usable
/// access_token fails fast instead of yielding an empty bearer.
public final class OauthAccessTokenCache {

    public static final Duration DEFAULT_REFRESH_MARGIN = Duration.ofSeconds(60);
    /// used when the token endpoint omits a positive expires_in: short, because
    /// refreshing too early is harmless while an over-long cache serves expired tokens
    public static final Duration DEFAULT_EXPIRES_IN = Duration.ofMinutes(1);

    private final Duration refreshMargin;
    private final Duration defaultExpiresIn;
    private final Clock clock;
    private volatile CachedAccessToken cached;

    private record CachedAccessToken(String value, Instant refreshAt) {

    }

    public OauthAccessTokenCache() {
        this(DEFAULT_REFRESH_MARGIN, DEFAULT_EXPIRES_IN, Clock.systemUTC());
    }

    public OauthAccessTokenCache(Duration refreshMargin, Duration defaultExpiresIn, Clock clock) {
        this.refreshMargin = refreshMargin;
        this.defaultExpiresIn = defaultExpiresIn;
        this.clock = clock;
    }

    /// Returns a valid access token, executing the grant when no token is
    /// cached or the cached one is about to expire.
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
