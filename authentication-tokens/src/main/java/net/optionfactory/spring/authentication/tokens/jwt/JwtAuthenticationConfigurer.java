package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public interface JwtAuthenticationConfigurer<SELF> {

    SELF claimsVerifier(JWTClaimsSetVerifier principal);

    default SELF claims(Duration maxClockSkew, Customizer<ClaimsVerifierConfigurer> c) {
        final var builder = new ClaimsVerifierBuilder(maxClockSkew);
        c.customize(builder);
        return claimsVerifier(builder.build());
    }

    SELF authorities(JwtAuthoritiesConverter a);

    default SELF authorities(GrantedAuthority... as) {
        final var al = List.of(as);
        return authorities((header, claims) -> al);
    }

    default SELF authorities(String... as) {
        final var al = Stream.of(as).map(SimpleGrantedAuthority::new).toList();
        return authorities((header, claims) -> al);
    }

    SELF principal(JwtPrincipalConverter principal);

    default SELF principal(Object principal) {
        return principal((Header header, JWTClaimsSet claims) -> principal);
    }

    public interface ClaimsVerifierConfigurer {

        ClaimsVerifierConfigurer audience(String... aud);

        ClaimsVerifierConfigurer require(String k);

        ClaimsVerifierConfigurer exact(String k, Object v);

        ClaimsVerifierConfigurer prohibit(String k);

    }

    public static class ClaimsVerifierBuilder implements ClaimsVerifierConfigurer {

        private final Duration maxClockSkew;
        private final Set<String> audiences = new HashSet<>();
        private final JWTClaimsSet.Builder exactMatch = new JWTClaimsSet.Builder();
        private final Set<String> required = new HashSet<>();
        private final Set<String> prohibited = new HashSet<>();

        public ClaimsVerifierBuilder(Duration maxClockSkew) {
            this.maxClockSkew = maxClockSkew;
        }

        @Override
        public ClaimsVerifierBuilder audience(String... auds) {
            for (final var aud : auds) {
                audiences.add(aud);
            }
            return this;
        }

        @Override
        public ClaimsVerifierBuilder require(String k) {
            required.add(k);
            return this;
        }

        @Override
        public ClaimsVerifierBuilder exact(String k, Object v) {
            exactMatch.claim(k, v);
            return this;
        }

        @Override
        public ClaimsVerifierBuilder prohibit(String k) {
            prohibited.add(k);
            return this;
        }

        public DefaultJWTClaimsVerifier<SecurityContext> build() {
            final var v = new DefaultJWTClaimsVerifier<>(
                    audiences.isEmpty() ? null : audiences,
                    exactMatch.build(),
                    required,
                    prohibited
            );
            v.setMaxClockSkew((int) maxClockSkew.toSeconds());
            return v;
        }

    }
}
