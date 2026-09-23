package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/// Which claims a JWT must carry, and which values they must hold, for it to be accepted. Every
/// `jws(...)` and `jwe(...)` configuration states one, and there are three kinds:
///
/// - [#issuer(String)] and [#audience(String, String...)] start a [Standard] policy. The token must
///   carry an `exp` and must not have expired, and it must name the configured issuer and/or one of
///   the configured audiences. A standard policy can only be started from one of the two, so a token
///   issued for another service — by the same issuer, or by anyone else holding the same key — cannot
///   pass it.
/// - [#permissive()] checks `exp` and `nbf` only when the token carries them, and neither issuer nor
///   audience: for tokens that carry none of them, typically issued by a third party. It can still pin
///   the claims such a token does carry, and it is spelled out at the call site, so it is never chosen
///   by accident.
/// - [#custom(JWTClaimsSetVerifier)] delegates to any Nimbus claims verifier.
///
/// Policies are immutable: every refinement returns a new policy, so one can be defined once and
/// shared. The clock skew tolerated on `exp` and `nbf` defaults to 60 seconds.
public sealed interface ClaimsPolicy permits ClaimsPolicy.Standard, ClaimsPolicy.Permissive, ClaimsPolicy.Custom {

    Duration DEFAULT_CLOCK_SKEW = Duration.ofSeconds(DefaultJWTClaimsVerifier.DEFAULT_MAX_CLOCK_SKEW_SECONDS);

    /// @return the verifier enforcing this policy
    JWTClaimsSetVerifier<SecurityContext> verifier();

    /// @param issuer the `iss` a token must carry
    /// @return a standard policy requiring that issuer
    static Standard issuer(String issuer) {
        Assert.hasText(issuer, "issuer cannot be empty");
        return new Standard(issuer, Set.of(), Map.of(), Set.of(), Set.of(), DEFAULT_CLOCK_SKEW);
    }

    /// @param audience an `aud` the token may carry
    /// @param more further accepted audiences
    /// @return a standard policy requiring the token to name at least one of the audiences
    static Standard audience(String audience, String... more) {
        return new Standard(null, audiences(audience, more), Map.of(), Set.of(), Set.of(), DEFAULT_CLOCK_SKEW);
    }

    /// @return a policy checking `exp` and `nbf` only when present, and neither issuer nor audience
    static Permissive permissive() {
        return new Permissive(Map.of(), Set.of(), Set.of(), DEFAULT_CLOCK_SKEW);
    }

    /// @param verifier the Nimbus verifier to delegate to
    /// @return a policy enforcing whatever that verifier does
    static Custom custom(JWTClaimsSetVerifier<SecurityContext> verifier) {
        Assert.notNull(verifier, "verifier cannot be null");
        return new Custom(verifier);
    }

    private static Set<String> audiences(String audience, String... more) {
        final var all = Stream.concat(Stream.of(audience), Stream.of(more)).toList();
        all.forEach(a -> Assert.hasText(a, "audience cannot be empty"));
        return Set.copyOf(all);
    }

    private static DefaultJWTClaimsVerifier<SecurityContext> verifier(@Nullable Set<String> audiences, Map<String, Object> exact, Set<String> required, Set<String> prohibited, Duration clockSkew) {
        final var exactMatch = new JWTClaimsSet.Builder();
        exact.forEach(exactMatch::claim);
        final var v = new DefaultJWTClaimsVerifier<SecurityContext>(audiences, exactMatch.build(), required, prohibited);
        v.setMaxClockSkew((int) clockSkew.toSeconds());
        return v;
    }

    private static <T> Set<T> with(Set<T> set, T value) {
        final var copy = new HashSet<>(set);
        copy.add(value);
        return Set.copyOf(copy);
    }

    private static Map<String, Object> with(Map<String, Object> map, String key, Object value) {
        Assert.hasText(key, "claim cannot be empty");
        Assert.notNull(value, "exact value cannot be null");
        final var copy = new HashMap<>(map);
        copy.put(key, value);
        return Map.copyOf(copy);
    }

    /// Requires an `exp`, and the configured issuer and/or one of the configured audiences.
    ///
    /// @param issuer the `iss` required, if any
    /// @param audiences the `aud` values accepted, if any: a token must name at least one
    /// @param exact claims that must hold exactly these values
    /// @param required claims that must be present, besides `exp`
    /// @param prohibited claims that must be absent
    /// @param clockSkew the skew tolerated on `exp` and `nbf`
    public record Standard(@Nullable String issuer, Set<String> audiences, Map<String, Object> exact, Set<String> required, Set<String> prohibited, Duration clockSkew) implements ClaimsPolicy {

        public Standard {
            Assert.isTrue(issuer != null || !audiences.isEmpty(), "a standard claims policy requires an issuer or an audience");
            audiences = Set.copyOf(audiences);
            exact = Map.copyOf(exact);
            required = Set.copyOf(required);
            prohibited = Set.copyOf(prohibited);
            Assert.notNull(clockSkew, "clockSkew cannot be null");
        }

        public Standard issuer(String issuer) {
            Assert.hasText(issuer, "issuer cannot be empty");
            return new Standard(issuer, audiences, exact, required, prohibited, clockSkew);
        }

        public Standard audience(String audience, String... more) {
            final var all = new HashSet<>(audiences);
            all.addAll(ClaimsPolicy.audiences(audience, more));
            return new Standard(issuer, all, exact, required, prohibited, clockSkew);
        }

        public Standard exact(String claim, Object value) {
            return new Standard(issuer, audiences, with(exact, claim, value), required, prohibited, clockSkew);
        }

        public Standard require(String claim) {
            return new Standard(issuer, audiences, exact, with(required, claim), prohibited, clockSkew);
        }

        public Standard prohibit(String claim) {
            return new Standard(issuer, audiences, exact, required, with(prohibited, claim), clockSkew);
        }

        public Standard clockSkew(Duration clockSkew) {
            return new Standard(issuer, audiences, exact, required, prohibited, clockSkew);
        }

        @Override
        public JWTClaimsSetVerifier<SecurityContext> verifier() {
            final var exactWithIssuer = issuer == null ? exact : with(exact, "iss", issuer);
            return ClaimsPolicy.verifier(audiences.isEmpty() ? null : audiences, exactWithIssuer, with(required, "exp"), prohibited, clockSkew);
        }
    }

    /// Checks `exp` and `nbf` only when the token carries them, and neither issuer nor audience.
    ///
    /// @param exact claims that must hold exactly these values
    /// @param required claims that must be present
    /// @param prohibited claims that must be absent
    /// @param clockSkew the skew tolerated on `exp` and `nbf`
    public record Permissive(Map<String, Object> exact, Set<String> required, Set<String> prohibited, Duration clockSkew) implements ClaimsPolicy {

        public Permissive {
            exact = Map.copyOf(exact);
            required = Set.copyOf(required);
            prohibited = Set.copyOf(prohibited);
            Assert.notNull(clockSkew, "clockSkew cannot be null");
        }

        public Permissive exact(String claim, Object value) {
            return new Permissive(with(exact, claim, value), required, prohibited, clockSkew);
        }

        public Permissive require(String claim) {
            return new Permissive(exact, with(required, claim), prohibited, clockSkew);
        }

        public Permissive prohibit(String claim) {
            return new Permissive(exact, required, with(prohibited, claim), clockSkew);
        }

        public Permissive clockSkew(Duration clockSkew) {
            return new Permissive(exact, required, prohibited, clockSkew);
        }

        @Override
        public JWTClaimsSetVerifier<SecurityContext> verifier() {
            return ClaimsPolicy.verifier(null, exact, required, prohibited, clockSkew);
        }
    }

    /// Delegates to a Nimbus claims verifier.
    ///
    /// @param verifier the verifier to delegate to
    public record Custom(JWTClaimsSetVerifier<SecurityContext> verifier) implements ClaimsPolicy {

        public Custom {
            Assert.notNull(verifier, "verifier cannot be null");
        }
    }
}
