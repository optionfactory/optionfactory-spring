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
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Locale;
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
/// - **Symmetric JWE over raw claims** — `decrypt(SecretKey)` / `decrypt(byte[])` (`AESDecrypter`),
///   or a `DirectDecrypter`. The shared secret is the trust root (only its holders can produce a
///   token we accept), so the decrypted payload is read directly as a JWT claims set. **Do not**
///   configure an inner verifier for this mode.
/// - **Asymmetric JWE with a nested JWS** — `decrypt(ECPrivateKey)` (`ECDHDecrypter`) or an
///   `RSADecrypter`. The encryption key is public, so anyone can mint a token that decrypts
///   successfully; the issuer is therefore authenticated by a **nested signed JWT**
///   (`JWE(JWS(claims))`) whose signature is verified via `verifier(...)` / `verify(...)`. An inner
///   verifier is **required**, and `build()` rejects an asymmetric decrypter that lacks one.
///
/// The runtime parsing mode is keyed on the presence of an inner verifier: if set, the decrypted
/// payload must be a `SignedJWT` and is verified before any claim is trusted; otherwise it is parsed
/// as raw claims. To remain on the symmetric raw-claims path, simply omit `verify(...)`.
public interface JweAuthenticationConfigurer extends JwtAuthenticationConfigurer<JweAuthenticationConfigurer> {

    JweAuthenticationConfigurer matchToken(JweMatcher matcher);

    default JweAuthenticationConfigurer match(Match m) {
        return matchToken((header, jwe) -> m);
    }

    JweAuthenticationConfigurer decrypter(JWEDecrypter decrypter);

    default JweAuthenticationConfigurer decrypt(SecretKey aesKey) {
        try {
            return decrypter(new AESDecrypter(aesKey));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    default JweAuthenticationConfigurer decrypt(byte[] aesKey) {
        try {
            return decrypter(new AESDecrypter(aesKey));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

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
    JweAuthenticationConfigurer verifier(JWSVerifier verifier);

    default JweAuthenticationConfigurer verify(RSAPublicKey key) {
        return verifier(new RSASSAVerifier(key));
    }

    default JweAuthenticationConfigurer verify(ECPublicKey key) {
        try {
            return verifier(new ECDSAVerifier(key));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    default JweAuthenticationConfigurer verify(byte[] shared) {
        try {
            return verifier(new MACVerifier(shared));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    default JweAuthenticationConfigurer verify(OctetKeyPair key) {
        try {
            return verifier(new Ed25519Verifier(key));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements JweAuthenticationConfigurer {

        private HeaderAndScheme hs = new HeaderAndScheme(HttpHeaders.AUTHORIZATION, "BEARER ");
        private JweMatcher tokenMatcher = (header, jwe) -> Match.STRICT;
        private JWEDecrypter decrypter;
        private JWSVerifier innerVerifier;
        private JWTClaimsSetVerifier<SecurityContext> claims = new DefaultJWTClaimsVerifier<>(null, null, null, null);
        private JwtAuthoritiesConverter authorities = new RolesGroupsAndScopesFromClaims(List.of());
        private JwtPrincipalConverter principal;

        @Override
        public Builder matchHeader(String header, String authScheme) {
            Assert.notNull(header, "header cannot be null");
            Assert.notNull(authScheme, "authScheme cannot be null");
            this.hs = new HeaderAndScheme(header, authScheme.toUpperCase(Locale.ROOT).trim() + " ");
            return this;
        }

        @Override
        public JweAuthenticationConfigurer matchToken(JweMatcher matcher) {
            Assert.notNull(matcher, "JweMatcher cannot be null");
            this.tokenMatcher = matcher;
            return this;
        }

        @Override
        public Builder decrypter(JWEDecrypter decrypter) {
            Assert.notNull(decrypter, "JWEDecrypter cannot be null");
            this.decrypter = decrypter;
            return this;
        }

        @Override
        public Builder verifier(JWSVerifier verifier) {
            Assert.notNull(verifier, "JWSVerifier cannot be null");
            this.innerVerifier = verifier;
            return this;
        }

        @Override
        public Builder claimsVerifier(JWTClaimsSetVerifier<SecurityContext> claims) {
            Assert.notNull(claims, "JWTClaimsSetVerifier cannot be null");
            this.claims = claims;
            return this;
        }

        @Override
        public Builder authorities(JwtAuthoritiesConverter authorities) {
            Assert.notNull(authorities, "JwtAuthoritiesConverter cannot be null");
            this.authorities = authorities;
            return this;
        }

        @Override
        public Builder principal(JwtPrincipalConverter principal) {
            Assert.notNull(principal, "JwtPrincipalConverter cannot be null");
            this.principal = principal;
            return this;
        }

        public JweProcessor build() {
            Assert.notNull(hs, "HeaderAndSchemeMatcher must be configured");
            Assert.notNull(tokenMatcher, "JweMatcher must be configured");
            Assert.notNull(decrypter, "JWEDecrypter must be configured");
            final boolean symmetric = decrypter instanceof AESDecrypter || decrypter instanceof DirectDecrypter;
            if (!symmetric) {
                Assert.notNull(innerVerifier, "JWSVerifier for the inner (nested) JWS is required for asymmetric JWE (ECDH/RSA): the public key lets anyone encrypt, so the issuer must be authenticated by an inner signature");
            }
            Assert.notNull(principal, "JwtPrincipalConverter must be configured");
            return new JwtTokenProcessor.JweProcessor(hs, tokenMatcher, decrypter, innerVerifier, claims, authorities, principal);
        }

    }
}
