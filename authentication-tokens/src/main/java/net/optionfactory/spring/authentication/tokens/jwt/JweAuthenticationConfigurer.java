package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.AESDecrypter;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.OctetKeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import javax.crypto.SecretKey;
import net.optionfactory.spring.authentication.tokens.HeaderAndScheme;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor.JweProcessor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;

/// Configures JWE (encrypted JWT) bearer-token authentication.
///
/// JWE provides **confidentiality** but not, by itself, **authenticity**: decrypting a token only
/// proves it was encrypted to us, not who authored it. Two trust models are supported, selected
/// automatically from the configured decrypter:
///
/// - **Symmetric JWE over raw claims**: `decrypt(SecretKey)` / `decrypt(byte[])` (`AESDecrypter`),
///   or a `DirectDecrypter`. The shared secret is the trust root (only its holders can produce a
///   token we accept), so the decrypted payload is read directly as a JWT claims set. **Do not**
///   configure an inner verifier for this mode.
/// - **Asymmetric JWE with a nested JWS**: `decrypt(ECPrivateKey)` (`ECDHDecrypter`) or an
///   `RSADecrypter`. The encryption key is public, so anyone can mint a token that decrypts
///   successfully; the issuer is therefore authenticated by a **nested signed JWT**
///   (`JWE(JWS(claims))`) whose signature is verified via `verifier(...)` / `verify(...)`. An inner
///   verifier is **required**, and `build()` rejects an asymmetric decrypter that lacks one.
///
/// The runtime parsing mode is keyed on the presence of an inner verifier: if set, the decrypted
/// payload must be a `SignedJWT` and is verified before any claim is trusted; otherwise it is parsed
/// as raw claims. To remain on the symmetric raw-claims path, simply omit `verify(...)`. Only
/// `AESDecrypter` and `DirectDecrypter` count as symmetric: any other decrypter requires an inner
/// verifier.
///
/// ```java
/// c.jwe(ClaimsPolicy.issuer("https://issuer.example.com").audience("my-service"), jwe -> {
///     jwe.decrypt(recipientPrivateKey);
///     jwe.verify(issuerPublicKey);
///     jwe.principal((header, claims) -> claims.getSubject());
/// });
/// ```
///
/// The token is looked for on `Authorization: Bearer` by default, and claimed [Match#STRICT]ly.
public interface JweAuthenticationConfigurer extends JwtAuthenticationConfigurer<JweAuthenticationConfigurer> {

    /// Decides, per token, whether this configuration claims it, tries it or skips it: see
    /// [JwtTokenProcessor] for how several configurations share a header.
    ///
    /// @param matcher inspects the still encrypted token
    /// @return this configurer
    JweAuthenticationConfigurer matchToken(JweMatcher matcher);

    /// Treats every token found on the header the same way, as
    /// `JwsAuthenticationConfigurer.matchToken(Match)` does for signed tokens.
    ///
    /// @param m how every token is treated
    /// @return this configurer
    default JweAuthenticationConfigurer matchToken(Match m) {
        return matchToken((header, jwe) -> m);
    }

    /// @param decrypter decrypts the token; required, and deciding the trust model as described
    /// above
    /// @return this configurer
    JweAuthenticationConfigurer decrypter(JWEDecrypter decrypter);

    /// Decrypts tokens whose content key is wrapped with AES key wrap (`A*KW`, `A*GCMKW`): the
    /// symmetric mode, where the key holders are trusted as issuers.
    ///
    /// @param aesKey the shared key
    /// @return this configurer
    /// @throws IllegalStateException when the key length is not an AES one
    default JweAuthenticationConfigurer decrypt(SecretKey aesKey) {
        try {
            return decrypter(new AESDecrypter(aesKey));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// As [#decrypt(SecretKey)].
    ///
    /// @param aesKey the shared key bytes
    /// @return this configurer
    /// @throws IllegalStateException when the key length is not an AES one
    default JweAuthenticationConfigurer decrypt(byte[] aesKey) {
        try {
            return decrypter(new AESDecrypter(aesKey));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// Decrypts `ECDH-ES*` tokens: the asymmetric mode, requiring an inner verifier.
    ///
    /// @param key this recipient's private key
    /// @return this configurer
    /// @throws IllegalStateException when the key's curve is not supported
    default JweAuthenticationConfigurer decrypt(ECPrivateKey key) {
        try {
            return decrypter(new ECDHDecrypter(key));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// Sets the verifier for the inner (nested) JWS. When set, the decrypted payload MUST be a
    /// signed JWT whose signature is verified against this key before any claim is trusted.
    /// Mandatory for asymmetric JWE (ECDH/RSA): the public key lets anyone encrypt, so only the inner
    /// signature authenticates the issuer. Optional for symmetric JWE (AES/direct), where the shared
    /// secret is the trust root and raw claims may be accepted without an inner signature.
    ///
    /// @param verifier verifies the inner token's signature
    /// @return this configurer
    JweAuthenticationConfigurer verifier(JWSVerifier verifier);

    /// Verifies inner `RS*` and `PS*` signatures.
    ///
    /// @param key the issuer's public key
    /// @return this configurer
    default JweAuthenticationConfigurer verify(RSAPublicKey key) {
        return verifier(new RSASSAVerifier(key));
    }

    /// Verifies inner `ES*` signatures.
    ///
    /// @param key the issuer's public key
    /// @return this configurer
    /// @throws IllegalStateException when the key's curve is not supported
    default JweAuthenticationConfigurer verify(ECPublicKey key) {
        try {
            return verifier(new ECDSAVerifier(key));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// Verifies inner `HS*` signatures.
    ///
    /// @param shared the shared secret, at least 256 bits long
    /// @return this configurer
    /// @throws IllegalStateException when the secret is shorter than 256 bits
    default JweAuthenticationConfigurer verify(byte[] shared) {
        try {
            return verifier(new MACVerifier(shared));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// Verifies inner `EdDSA` (Ed25519) signatures.
    ///
    /// @param key the issuer's public key
    /// @return this configurer
    /// @throws IllegalStateException when the key is not an Ed25519 key
    default JweAuthenticationConfigurer verify(OctetKeyPair key) {
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

    /// Collects a JWE configuration and builds its processor.
    public static class Builder implements JweAuthenticationConfigurer {

        private HeaderAndScheme hs = new HeaderAndScheme(HttpHeaders.AUTHORIZATION, "BEARER ");
        private JweMatcher tokenMatcher = (header, jwe) -> Match.STRICT;
        private JWEDecrypter decrypter;
        private JWSVerifier innerVerifier;
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
        public JweAuthenticationConfigurer matchToken(JweMatcher matcher) {
            Assert.notNull(matcher, "JweMatcher cannot be null");
            this.tokenMatcher = matcher;
            return this;
        }

        /// @throws IllegalArgumentException when the decrypter is `null`
        @Override
        public Builder decrypter(JWEDecrypter decrypter) {
            Assert.notNull(decrypter, "JWEDecrypter cannot be null");
            this.decrypter = decrypter;
            return this;
        }

        /// @throws IllegalArgumentException when the verifier is `null`
        @Override
        public Builder verifier(JWSVerifier verifier) {
            Assert.notNull(verifier, "JWSVerifier cannot be null");
            this.innerVerifier = verifier;
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
        /// @throws IllegalArgumentException when no decrypter or no principal is configured, or
        /// when an asymmetric decrypter has no inner verifier
        public JweProcessor build() {
            Assert.notNull(hs, "HeaderAndSchemeMatcher must be configured");
            Assert.notNull(tokenMatcher, "JweMatcher must be configured");
            Assert.notNull(decrypter, "JWEDecrypter must be configured");
            final boolean symmetric = decrypter instanceof AESDecrypter || decrypter instanceof DirectDecrypter;
            if (!symmetric) {
                Assert.notNull(innerVerifier, "JWSVerifier for the inner (nested) JWS is required for asymmetric JWE (ECDH/RSA): the public key lets anyone encrypt, so the issuer must be authenticated by an inner signature");
            }
            Assert.notNull(principal, "JwtPrincipalConverter must be configured");
            return new JwtTokenProcessor.JweProcessor(hs, tokenMatcher, decrypter, innerVerifier, claims.verifier(), authorities, principal);
        }

    }
}
