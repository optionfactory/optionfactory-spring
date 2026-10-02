package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.optionfactory.spring.authentication.tokens.HeaderAndScheme;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

public class ClaimsPolicyTest {

    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes();
    private static final HeaderAndScheme BEARER = new HeaderAndScheme("Authorization", "Bearer");
    private static final Date IN_AN_HOUR = Date.from(Instant.now().plusSeconds(3600));

    private static JwtTokenProcessor processor(ClaimsPolicy policy) {
        final var b = JwsAuthenticationConfigurer.builder(policy);
        b.verify(KEY);
        b.principal("svc");
        return new JwtTokenProcessor(List.of(b.build()), List.of());
    }

    private static String token(JWTClaimsSet claims) throws Exception {
        final var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(KEY));
        return jwt.serialize();
    }

    private static boolean accepts(ClaimsPolicy policy, JWTClaimsSet claims) throws Exception {
        try {
            return processor(policy).process(BEARER, token(claims)) != null;
        } catch (BadCredentialsException ex) {
            return false;
        }
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer("my-issuer").audience("example.com").expirationTime(IN_AN_HOUR);
    }

    @Test
    public void aStandardPolicyAcceptsAMatchingUnexpiredToken() throws Exception {
        Assertions.assertTrue(accepts(ClaimsPolicy.issuer("my-issuer").audience("example.com"), claims().build()), "a token naming the issuer and the audience, and not expired, is accepted");
    }

    @Test
    public void aStandardPolicyRequiresAnExpiration() throws Exception {
        Assertions.assertFalse(accepts(ClaimsPolicy.issuer("my-issuer"), claims().expirationTime(null).build()), "a standard policy rejects a token without exp");
    }

    @Test
    public void aStandardPolicyRejectsAnotherIssuer() throws Exception {
        Assertions.assertFalse(accepts(ClaimsPolicy.issuer("my-issuer"), claims().issuer("someone-else").build()), "a token from another issuer is rejected");
    }

    @Test
    public void aStandardPolicyRejectsATokenForAnotherAudience() throws Exception {
        Assertions.assertFalse(accepts(ClaimsPolicy.audience("example.com"), claims().audience("another-service").build()), "a token issued for another service is rejected");
    }

    @Test
    public void aStandardPolicyAcceptsAnyOfItsAudiences() throws Exception {
        Assertions.assertTrue(accepts(ClaimsPolicy.audience("example.com", "example.org"), claims().audience("example.org").build()), "naming any one of the configured audiences is enough");
    }

    @Test
    public void aStandardPolicyCannotBeMadeWithoutAnIssuerOrAnAudience() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ClaimsPolicy.Standard(null, Set.of(), Map.of(), Set.of(), Set.of(), Duration.ZERO), "a standard policy must pin an issuer or an audience");
    }

    @Test
    public void aPermissivePolicyAcceptsATokenWithoutExpirationIssuerOrAudience() throws Exception {
        Assertions.assertTrue(accepts(ClaimsPolicy.permissive(), new JWTClaimsSet.Builder().subject("third-party").build()), "the explicit opt-out accepts third-party tokens lacking exp, iss and aud");
    }

    @Test
    public void aPermissivePolicyStillRejectsAnExpiredToken() throws Exception {
        Assertions.assertFalse(accepts(ClaimsPolicy.permissive(), new JWTClaimsSet.Builder().expirationTime(Date.from(Instant.now().minusSeconds(3600))).build()), "an exp, when present, is still enforced");
    }

    @Test
    public void aPermissivePolicyCanPinAClaimTheThirdPartySends() throws Exception {
        final var policy = ClaimsPolicy.permissive().exact("client_id", "acme");
        Assertions.assertTrue(accepts(policy, new JWTClaimsSet.Builder().claim("client_id", "acme").build()), "the pinned value is accepted");
        Assertions.assertFalse(accepts(policy, new JWTClaimsSet.Builder().claim("client_id", "evil").build()), "any other value is rejected");
    }

    @Test
    public void refiningAPolicyLeavesTheOriginalUnchanged() throws Exception {
        final var base = ClaimsPolicy.issuer("my-issuer");
        final var refined = base.audience("example.com");
        final var noAudience = claims().audience((String) null).build();
        Assertions.assertTrue(accepts(base, noAudience), "the original policy is unaffected by the refinement");
        Assertions.assertFalse(accepts(refined, noAudience), "the refined policy requires the audience");
    }

    @Test
    public void theClockSkewIsTolerated() throws Exception {
        final var justExpired = claims().expirationTime(Date.from(Instant.now().minusSeconds(30))).build();
        Assertions.assertTrue(accepts(ClaimsPolicy.issuer("my-issuer"), justExpired), "a token expired 30s ago is within the default 60s skew");
        Assertions.assertFalse(accepts(ClaimsPolicy.issuer("my-issuer").clockSkew(Duration.ZERO), justExpired), "without skew the expired token is rejected");
    }

    @Test
    public void aCustomPolicyDelegatesToItsVerifier() throws Exception {
        final ClaimsPolicy rejectEverything = ClaimsPolicy.custom((claims, context) -> {
            throw new BadJWTException("no");
        });
        Assertions.assertFalse(accepts(rejectEverything, claims().build()), "a custom policy rejects what its verifier rejects");
    }

    @Test
    public void aTokenNotYetValidIsRejected() throws Exception {
        final var notBefore = Date.from(Instant.now().plusSeconds(3600));
        Assertions.assertFalse(accepts(ClaimsPolicy.issuer("my-issuer"), claims().notBeforeTime(notBefore).build()), "a standard policy rejects a token whose nbf is in the future");
        Assertions.assertFalse(accepts(ClaimsPolicy.permissive(), new JWTClaimsSet.Builder().notBeforeTime(notBefore).build()), "a permissive policy enforces an nbf when present");
    }

    @Test
    public void aStandardPolicyRejectsATokenWithoutAnAudienceWhenItRequiresOne() throws Exception {
        Assertions.assertFalse(accepts(ClaimsPolicy.audience("example.com"), claims().audience((String) null).build()), "a token naming no audience does not name the configured one");
    }

    @Test
    public void requiredClaimsMustBePresent() throws Exception {
        Assertions.assertFalse(accepts(ClaimsPolicy.issuer("my-issuer").require("jti"), claims().build()), "a standard policy rejects a token lacking a required claim");
        Assertions.assertTrue(accepts(ClaimsPolicy.issuer("my-issuer").require("jti"), claims().jwtID("an-id").build()), "the required claim is accepted with any value");
        Assertions.assertFalse(accepts(ClaimsPolicy.permissive().require("sub"), new JWTClaimsSet.Builder().build()), "a permissive policy rejects a token lacking a required claim");
    }

    @Test
    public void prohibitedClaimsMustBeAbsent() throws Exception {
        Assertions.assertFalse(accepts(ClaimsPolicy.issuer("my-issuer").prohibit("act"), claims().claim("act", "someone").build()), "a standard policy rejects a token carrying a prohibited claim");
        Assertions.assertFalse(accepts(ClaimsPolicy.permissive().prohibit("act"), new JWTClaimsSet.Builder().claim("act", "someone").build()), "a permissive policy rejects a token carrying a prohibited claim");
        Assertions.assertTrue(accepts(ClaimsPolicy.permissive().prohibit("act"), new JWTClaimsSet.Builder().build()), "a token without the prohibited claim is accepted");
    }

    @Test
    public void anIntegerClaimIsPinnedWithALong() throws Exception {
        final var token = new JWTClaimsSet.Builder().claim("level", 1).build();
        Assertions.assertTrue(accepts(ClaimsPolicy.permissive().exact("level", 1L), token), "json integers are parsed as longs, so a long matches");
    }

    @Test
    public void anIntegralClaimIsPinnedByValueWhateverItsJavaType() throws Exception {
        final var token = new JWTClaimsSet.Builder().claim("level", 1).build();
        Assertions.assertTrue(accepts(ClaimsPolicy.permissive().exact("level", 1), token), "an int pins the json integer it equals");
        Assertions.assertTrue(accepts(ClaimsPolicy.permissive().exact("level", (short) 1), token), "a short pins the json integer it equals");
        Assertions.assertTrue(accepts(ClaimsPolicy.permissive().exact("level", (byte) 1), token), "a byte pins the json integer it equals");
        Assertions.assertTrue(accepts(ClaimsPolicy.issuer("my-issuer").exact("level", 1), claims().claim("level", 1).build()), "a standard policy pins an int by value too");
        Assertions.assertFalse(accepts(ClaimsPolicy.permissive().exact("level", 2), token), "an int still rejects a different integer");
    }

    @Test
    public void aDecimalClaimIsPinnedByValueWhateverItsJavaType() throws Exception {
        final var token = new JWTClaimsSet.Builder().claim("ratio", 1.5).build();
        Assertions.assertTrue(accepts(ClaimsPolicy.permissive().exact("ratio", 1.5), token), "a double pins the json decimal it equals");
        Assertions.assertTrue(accepts(ClaimsPolicy.permissive().exact("ratio", 1.5f), token), "a float pins the json decimal it equals");
        Assertions.assertTrue(accepts(ClaimsPolicy.permissive().exact("ratio", 0.1f), new JWTClaimsSet.Builder().claim("ratio", 0.1).build()), "a float pins the decimal it is written as, not its binary approximation");
        Assertions.assertFalse(accepts(ClaimsPolicy.permissive().exact("ratio", 1), token), "an integer does not match a decimal with a fractional part");
        Assertions.assertFalse(accepts(ClaimsPolicy.permissive().exact("level", 1.5), new JWTClaimsSet.Builder().claim("level", 1).build()), "a decimal with a fractional part does not match an integer");
    }

    @Test
    public void aRefinedIssuerReplacesThePreviousOne() throws Exception {
        final var policy = ClaimsPolicy.issuer("old-issuer").issuer("my-issuer");
        Assertions.assertTrue(accepts(policy, claims().build()), "the last configured issuer is the one required");
        Assertions.assertFalse(accepts(policy, claims().issuer("old-issuer").build()), "the replaced issuer is no longer accepted");
    }

    @Test
    public void blankIssuersAndAudiencesAreRefused() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> ClaimsPolicy.issuer(" "), "a blank issuer would pin nothing");
        Assertions.assertThrows(IllegalArgumentException.class, () -> ClaimsPolicy.audience(""), "an empty audience would pin nothing");
        Assertions.assertThrows(IllegalArgumentException.class, () -> ClaimsPolicy.audience("example.com", ""), "an empty additional audience would pin nothing");
    }
}
