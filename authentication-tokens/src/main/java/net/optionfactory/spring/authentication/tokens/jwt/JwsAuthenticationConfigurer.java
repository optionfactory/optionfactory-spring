package net.optionfactory.spring.authentication.tokens.jwt;

import net.optionfactory.spring.authentication.tokens.HeaderAndScheme;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor.JwsProcessor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;

public interface JwsAuthenticationConfigurer extends JwtAuthenticationConfigurer<JwsAuthenticationConfigurer> {

    JwsAuthenticationConfigurer matchToken(JwsMatcher matcher);

    default JwsAuthenticationConfigurer matchToken(Match m) {
        return matchToken((header, claims, jws) -> m);
    }

    JwsAuthenticationConfigurer verifier(JWSVerifier verifier);

    default JwsAuthenticationConfigurer verify(RSAPublicKey key) {
        return verifier(new RSASSAVerifier(key));
    }

    default JwsAuthenticationConfigurer verify(ECPublicKey key) {
        try {
            return verifier(new ECDSAVerifier(key));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    default JwsAuthenticationConfigurer verify(byte[] shared) {
        try {
            return verifier(new MACVerifier(shared));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    default JwsAuthenticationConfigurer verify(OctetKeyPair key) {
        try {
            return verifier(new Ed25519Verifier(key));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements JwsAuthenticationConfigurer {

        private HeaderAndScheme hs = new HeaderAndScheme(HttpHeaders.AUTHORIZATION, "BEARER ");
        private JwsMatcher tokenMatcher = (header, unverifiedClaims, jws) -> Match.STRICT;
        private JWSVerifier verifier;
        private JWTClaimsSetVerifier<SecurityContext> claims = new DefaultJWTClaimsVerifier<>(null, null, null, null);
        private JwtAuthoritiesConverter authorities = new RolesGroupsAndScopesFromClaims(List.of());
        private JwtPrincipalConverter principal;

        @Override
        public Builder matchHeader(String header, String authScheme) {
            Assert.notNull(header, "header cannot be null");
            Assert.notNull(authScheme, "authScheme cannot be null");
            this.hs = new HeaderAndScheme(header, authScheme.toUpperCase().trim() + " ");
            return this;
        }

        @Override
        public Builder matchToken(JwsMatcher matcher) {
            Assert.notNull(matcher, "JwsMatcher cannot be null");
            this.tokenMatcher = matcher;
            return this;
        }

        @Override
        public Builder verifier(JWSVerifier verifier) {
            Assert.notNull(verifier, "JwsMatcher cannot be null");
            this.verifier = verifier;
            return this;
        }

        @Override
        public Builder claimsVerifier(JWTClaimsSetVerifier claims) {
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

        public JwsProcessor build() {
            Assert.notNull(hs, "HeaderAndScheme must be configured");
            Assert.notNull(tokenMatcher, "JwsMatcher must be configured");
            Assert.notNull(verifier, "JWTClaimsSetVerifier must be configured");
            Assert.notNull(principal, "JwtPrincipalConverter must be configured");
            return new JwtTokenProcessor.JwsProcessor(hs, tokenMatcher, verifier, claims, authorities, principal);
        }

    }
}
