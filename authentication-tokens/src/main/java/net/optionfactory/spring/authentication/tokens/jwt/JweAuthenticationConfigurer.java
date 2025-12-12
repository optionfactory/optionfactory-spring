package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.crypto.AESDecrypter;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import java.security.interfaces.ECPrivateKey;
import java.util.List;
import javax.crypto.SecretKey;
import net.optionfactory.spring.authentication.tokens.HeaderAndScheme;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor.JweProcessor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements JweAuthenticationConfigurer {

        private HeaderAndScheme hs = new HeaderAndScheme(HttpHeaders.AUTHORIZATION, "BEARER ");
        private JweMatcher tokenMatcher = (header, jwe) -> Match.STRICT;
        private JWEDecrypter decrypter;
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
        public JweAuthenticationConfigurer matchToken(JweMatcher matcher) {
            Assert.notNull(principal, "JweMatcher cannot be null");
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

        public JweProcessor build() {
            Assert.notNull(hs, "HeaderAndSchemeMatcher must be configured");
            Assert.notNull(tokenMatcher, "JweMatcher must be configured");
            Assert.notNull(decrypter, "JWEDecrypter must be configured");
            Assert.notNull(principal, "JwtPrincipalConverter must be configured");
            return new JwtTokenProcessor.JweProcessor(hs, tokenMatcher, decrypter, claims, authorities, principal);
        }

    }
}
