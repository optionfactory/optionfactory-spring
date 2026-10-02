package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.OctetKeyPair;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import net.optionfactory.spring.authentication.tokens.HeaderAndScheme;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor.JwsProcessor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;

/// Configures JWS (signed JWT) token authentication, through
/// `HttpHeaderAuthentication.Configurer.jws(...)`.
///
/// A token is accepted once its signature verifies with the configured verifier and its claims
/// satisfy the [ClaimsPolicy]; its principal and authorities are then derived from the verified
/// claims. Unsecured tokens (`alg: none`) and encrypted ones are never accepted by a JWS
/// configuration, and a token signed with an algorithm the verifier does not support is rejected,
/// so a token cannot pick its own verification method: an HMAC-signed token is not checked against
/// an RSA public key used as a shared secret.
///
/// ```java
/// c.jws(ClaimsPolicy.issuer("https://issuer.example.com").audience("my-service"), jws -> {
///     jws.verify(issuerPublicKey);
///     jws.principal((header, claims) -> claims.getSubject());
/// });
/// ```
///
/// The token is looked for on `Authorization: Bearer` by default, and claimed [Match#STRICT]ly.
public interface JwsAuthenticationConfigurer extends JwtAuthenticationConfigurer<JwsAuthenticationConfigurer> {

    /// Decides, per token, whether this configuration claims it, tries it or skips it: see
    /// [JwtTokenProcessor] for how several configurations share a header.
    ///
    /// @param matcher inspects the unverified token
    /// @return this configurer
    JwsAuthenticationConfigurer matchToken(JwsMatcher matcher);

    /// Treats every token found on the header the same way.
    ///
    /// @param m how every token is treated
    /// @return this configurer
    default JwsAuthenticationConfigurer matchToken(Match m) {
        return matchToken((header, claims, jws) -> m);
    }

    /// @param verifier verifies the token's signature; required
    /// @return this configurer
    JwsAuthenticationConfigurer verifier(JWSVerifier verifier);

    /// Verifies `RS*` and `PS*` signatures.
    ///
    /// @param key the issuer's public key
    /// @return this configurer
    default JwsAuthenticationConfigurer verify(RSAPublicKey key) {
        return verifier(new RSASSAVerifier(key));
    }

    /// Verifies `ES*` signatures.
    ///
    /// @param key the issuer's public key
    /// @return this configurer
    /// @throws IllegalStateException when the key's curve is not supported
    default JwsAuthenticationConfigurer verify(ECPublicKey key) {
        try {
            return verifier(new ECDSAVerifier(key));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// Verifies `HS*` signatures. Anyone holding the secret can mint accepted tokens, so a
    /// [ClaimsPolicy] naming the audience keeps a token issued for another service sharing the
    /// secret from being accepted here.
    ///
    /// @param shared the shared secret, at least 256 bits long
    /// @return this configurer
    /// @throws IllegalStateException when the secret is shorter than 256 bits
    default JwsAuthenticationConfigurer verify(byte[] shared) {
        try {
            return verifier(new MACVerifier(shared));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// Verifies `EdDSA` (Ed25519) signatures.
    ///
    /// @param key the issuer's public key
    /// @return this configurer
    /// @throws IllegalStateException when the key is not an Ed25519 key
    default JwsAuthenticationConfigurer verify(OctetKeyPair key) {
        try {
            return verifier(new Ed25519Verifier(key));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// @param claims the claims a token must carry to be accepted
    /// @return a builder enforcing that policy
    public static Builder builder(ClaimsPolicy claims) {
        return new Builder(claims);
    }

    /// Collects a JWS configuration and builds its processor.
    public static class Builder implements JwsAuthenticationConfigurer {

        private HeaderAndScheme hs = new HeaderAndScheme(HttpHeaders.AUTHORIZATION, "BEARER ");
        private JwsMatcher tokenMatcher = (header, unverifiedClaims, jws) -> Match.STRICT;
        private JWSVerifier verifier;
        private final ClaimsPolicy claims;
        private JwtAuthoritiesConverter authorities = new RolesGroupsAndScopesFromClaims(List.of());
        private JwtPrincipalConverter principal;

        /// @param claims the claims a token must carry to be accepted
        /// @throws IllegalArgumentException when the policy is `null`
        public Builder(ClaimsPolicy claims) {
            Assert.notNull(claims, "ClaimsPolicy cannot be null");
            this.claims = claims;
        }

        /// @throws IllegalArgumentException when the header or the scheme is `null`
        @Override
        public Builder matchHeader(String header, String authScheme) {
            Assert.notNull(header, "header cannot be null");
            Assert.notNull(authScheme, "authScheme cannot be null");
            this.hs = new HeaderAndScheme(header, authScheme);
            return this;
        }

        /// @throws IllegalArgumentException when the matcher is `null`
        @Override
        public Builder matchToken(JwsMatcher matcher) {
            Assert.notNull(matcher, "JwsMatcher cannot be null");
            this.tokenMatcher = matcher;
            return this;
        }

        /// @throws IllegalArgumentException when the verifier is `null`
        @Override
        public Builder verifier(JWSVerifier verifier) {
            Assert.notNull(verifier, "JWSVerifier cannot be null");
            this.verifier = verifier;
            return this;
        }

        /// @throws IllegalArgumentException when the converter is `null`
        @Override
        public Builder authorities(JwtAuthoritiesConverter authorities) {
            Assert.notNull(authorities, "JwtAuthoritiesConverter cannot be null");
            this.authorities = authorities;
            return this;
        }

        /// @throws IllegalArgumentException when the converter is `null`
        @Override
        public Builder principal(JwtPrincipalConverter principal) {
            Assert.notNull(principal, "JwtPrincipalConverter cannot be null");
            this.principal = principal;
            return this;
        }

        /// @return the processor for this configuration
        /// @throws IllegalArgumentException when no verifier or no principal is configured
        public JwsProcessor build() {
            Assert.notNull(hs, "HeaderAndScheme must be configured");
            Assert.notNull(tokenMatcher, "JwsMatcher must be configured");
            Assert.notNull(verifier, "JWSVerifier must be configured");
            Assert.notNull(principal, "JwtPrincipalConverter must be configured");
            return new JwtTokenProcessor.JwsProcessor(hs, tokenMatcher, verifier, claims.verifier(), authorities, principal);
        }

    }
}
