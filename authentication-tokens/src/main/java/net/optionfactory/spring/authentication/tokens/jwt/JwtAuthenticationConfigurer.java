package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/// What the JWS and JWE configurations have in common: where the token is found, and what an
/// accepted token grants.
///
/// The token is looked for on `Authorization: Bearer` unless [#matchHeader(String, String)] says
/// otherwise. A principal must always be configured; the authorities default to
/// [RolesGroupsAndScopesFromClaims], and configuring any other replaces it.
///
/// @param <SELF> the concrete configurer, returned by every method for chaining
public interface JwtAuthenticationConfigurer<SELF> {

    /// @param a derives the authorities from the verified token
    /// @return this configurer
    SELF authorities(JwtAuthoritiesConverter a);

    /// Grants the same authorities to every accepted token, ignoring its claims.
    ///
    /// @param as the authorities granted
    /// @return this configurer
    default SELF authorities(GrantedAuthority... as) {
        final var al = List.of(as);
        return authorities((header, claims) -> al);
    }

    /// Grants the same authorities to every accepted token, ignoring its claims.
    ///
    /// @param as the names of the authorities granted, e.g. `ROLE_M2M`
    /// @return this configurer
    default SELF authorities(String... as) {
        final var al = Stream.of(as).map(SimpleGrantedAuthority::new).toList();
        return authorities((header, claims) -> al);
    }

    /// @param principal derives the principal from the verified token; a `null` principal rejects
    /// the token
    /// @return this configurer
    SELF principal(JwtPrincipalConverter principal);

    /// @param header the header carrying the token
    /// @param authScheme the scheme prefixing the token, matched case-insensitively, or blank for a
    /// header carrying the bare token
    /// @return this configurer
    SELF matchHeader(String header, String authScheme);

    /// Matches a header carrying the bare token, with no auth-scheme prefix.
    ///
    /// @param header the header carrying the token
    /// @return this configurer
    default SELF matchHeaderWithoutScheme(String header) {
        return matchHeader(header, "");
    }

    /// Gives every accepted token the same principal, typically naming the calling service.
    ///
    /// @param principal the principal of every accepted token
    /// @return this configurer
    default SELF principal(Object principal) {
        return principal((Header header, JWTClaimsSet claims) -> principal);
    }
}
