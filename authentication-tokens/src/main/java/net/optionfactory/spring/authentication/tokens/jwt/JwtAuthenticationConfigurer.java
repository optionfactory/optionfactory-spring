package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public interface JwtAuthenticationConfigurer<SELF> {

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

    SELF matchHeader(String header, String authScheme);

    /// Matches a header carrying the bare token, with no auth-scheme prefix.
    default SELF matchHeaderWithoutScheme(String header) {
        return matchHeader(header, "");
    }

    default SELF principal(Object principal) {
        return principal((Header header, JWTClaimsSet claims) -> principal);
    }
}
