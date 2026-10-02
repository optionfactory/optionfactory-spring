package net.optionfactory.spring.upstream.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class OauthAccessTokenCacheTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static Supplier<JsonNode> granting(AtomicInteger grants, String json) {
        return () -> {
            grants.incrementAndGet();
            return JsonMapper.builder().build().readTree(json);
        };
    }

    private static OauthAccessTokenCache cacheAt(Instant now) {
        return new OauthAccessTokenCache(Duration.ofSeconds(10), Duration.ofSeconds(30), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static int grantsAfter(String json, long... secondsElapsed) {
        final var grants = new AtomicInteger();
        final var grant = granting(grants, json);
        final var clock = new MutableClock(NOW);
        final var cache = new OauthAccessTokenCache(Duration.ofSeconds(10), Duration.ofSeconds(30), clock);
        cache.accessToken(grant);
        for (long elapsed : secondsElapsed) {
            clock.now = NOW.plusSeconds(elapsed);
            cache.accessToken(grant);
        }
        return grants.get();
    }

    @Test
    public void tokenIsCachedUntilExpiresInMinusTheMargin() {
        Assertions.assertEquals(1, grantsAfter("{\"access_token\":\"t\",\"expires_in\":100}", 89), "a 100s token with a 10s margin must still be cached at t+89s");
        Assertions.assertEquals(2, grantsAfter("{\"access_token\":\"t\",\"expires_in\":100}", 90), "a 100s token with a 10s margin must be refreshed at t+90s");
    }

    @Test
    public void missingExpiresInFallsBackToTheDefault() {
        Assertions.assertEquals(1, grantsAfter("{\"access_token\":\"t\"}", 19), "a token without expires_in must be cached for the 30s default minus the 10s margin");
        Assertions.assertEquals(2, grantsAfter("{\"access_token\":\"t\"}", 20), "a token without expires_in must be refreshed at the 30s default minus the 10s margin");
    }

    @Test
    public void nonPositiveExpiresInFallsBackToTheDefault() {
        Assertions.assertEquals(1, grantsAfter("{\"access_token\":\"t\",\"expires_in\":0}", 19), "a zero expires_in must be replaced by the default");
        Assertions.assertEquals(2, grantsAfter("{\"access_token\":\"t\",\"expires_in\":-5}", 20), "a negative expires_in must be replaced by the default");
    }

    @Test
    public void blankAccessTokenFailsFast() {
        final var cache = cacheAt(NOW);
        final var grant = granting(new AtomicInteger(), "{\"access_token\":\"  \",\"expires_in\":600}");
        Assertions.assertThrows(IllegalStateException.class, () -> cache.accessToken(grant), "a blank access_token must not be cached nor returned");
    }

    @Test
    public void nullAccessTokenFailsFast() {
        final var cache = cacheAt(NOW);
        final var grant = granting(new AtomicInteger(), "{\"access_token\":null,\"expires_in\":600}");
        Assertions.assertThrows(IllegalStateException.class, () -> cache.accessToken(grant), "a null access_token must not be cached nor returned");
    }

    @Test
    public void failedGrantIsRetriedOnTheNextCall() {
        final var cache = cacheAt(NOW);
        final var attempts = new AtomicInteger();
        final Supplier<JsonNode> flaky = () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("token endpoint down");
            }
            return JsonMapper.builder().build().readTree("{\"access_token\":\"t\",\"expires_in\":600}");
        };
        Assertions.assertThrows(IllegalStateException.class, () -> cache.accessToken(flaky), "the grant failure must reach the caller");
        Assertions.assertEquals("t", cache.accessToken(flaky), "a failed grant must not poison the cache");
    }

    private static final class MutableClock extends Clock {

        public Instant now;

        public MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
