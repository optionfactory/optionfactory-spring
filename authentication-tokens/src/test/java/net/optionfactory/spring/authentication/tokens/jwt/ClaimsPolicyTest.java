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
        Assertions.assertTrue(accepts(ClaimsPolicy.issuer("my-issuer").audience("example.com"), claims().build()));
    }

    @Test
    public void aStandardPolicyRequiresAnExpiration() throws Exception {
        Assertions.assertFalse(accepts(ClaimsPolicy.issuer("my-issuer"), claims().expirationTime(null).build()));
    }

    @Test
    public void aStandardPolicyRejectsAnotherIssuer() throws Exception {
        Assertions.assertFalse(accepts(ClaimsPolicy.issuer("my-issuer"), claims().issuer("someone-else").build()));
    }

    @Test
    public void aStandardPolicyRejectsATokenForAnotherAudience() throws Exception {
        Assertions.assertFalse(accepts(ClaimsPolicy.audience("example.com"), claims().audience("another-service").build()));
    }

    @Test
    public void aStandardPolicyAcceptsAnyOfItsAudiences() throws Exception {
        Assertions.assertTrue(accepts(ClaimsPolicy.audience("example.com", "example.org"), claims().audience("example.org").build()));
    }

    @Test
    public void aStandardPolicyCannotBeMadeWithoutAnIssuerOrAnAudience() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ClaimsPolicy.Standard(null, Set.of(), Map.of(), Set.of(), Set.of(), Duration.ZERO));
    }

    @Test
    public void aPermissivePolicyAcceptsATokenWithoutExpirationIssuerOrAudience() throws Exception {
        Assertions.assertTrue(accepts(ClaimsPolicy.permissive(), new JWTClaimsSet.Builder().subject("third-party").build()));
    }

    @Test
    public void aPermissivePolicyStillRejectsAnExpiredToken() throws Exception {
        Assertions.assertFalse(accepts(ClaimsPolicy.permissive(), new JWTClaimsSet.Builder().expirationTime(Date.from(Instant.now().minusSeconds(3600))).build()));
    }

    @Test
    public void aPermissivePolicyCanPinAClaimTheThirdPartySends() throws Exception {
        final var policy = ClaimsPolicy.permissive().exact("client_id", "acme");
        Assertions.assertTrue(accepts(policy, new JWTClaimsSet.Builder().claim("client_id", "acme").build()));
        Assertions.assertFalse(accepts(policy, new JWTClaimsSet.Builder().claim("client_id", "evil").build()));
    }

    @Test
    public void refiningAPolicyLeavesTheOriginalUnchanged() throws Exception {
        final var base = ClaimsPolicy.issuer("my-issuer");
        final var refined = base.audience("example.com");
        final var noAudience = claims().audience((String) null).build();
        Assertions.assertTrue(accepts(base, noAudience));
        Assertions.assertFalse(accepts(refined, noAudience));
    }

    @Test
    public void theClockSkewIsTolerated() throws Exception {
        final var justExpired = claims().expirationTime(Date.from(Instant.now().minusSeconds(30))).build();
        Assertions.assertTrue(accepts(ClaimsPolicy.issuer("my-issuer"), justExpired));
        Assertions.assertFalse(accepts(ClaimsPolicy.issuer("my-issuer").clockSkew(Duration.ZERO), justExpired));
    }

    @Test
    public void aCustomPolicyDelegatesToItsVerifier() throws Exception {
        final ClaimsPolicy rejectEverything = ClaimsPolicy.custom((claims, context) -> {
            throw new BadJWTException("no");
        });
        Assertions.assertFalse(accepts(rejectEverything, claims().build()));
    }
}
