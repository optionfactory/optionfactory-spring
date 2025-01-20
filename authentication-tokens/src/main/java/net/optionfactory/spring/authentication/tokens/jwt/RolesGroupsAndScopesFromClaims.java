package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class RolesGroupsAndScopesFromClaims implements JwtAuthoritiesConverter {

    private final List<? extends GrantedAuthority> defaultAuthorities;

    public RolesGroupsAndScopesFromClaims(List<? extends GrantedAuthority> defaultAuthorities) {
        this.defaultAuthorities = defaultAuthorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> convert(Header header, JWTClaimsSet claims) {
        try {
            final Object scopeOrNull = claims.getClaim("scope");
            final List<String> rolesOrNull = claims.getStringListClaim("roles");
            final List<String> groupsOrNull = claims.getStringListClaim("groups");
            final List<String> roles = rolesOrNull != null ? rolesOrNull : List.of();
            final List<String> groups = groupsOrNull != null ? groupsOrNull : List.of();
            final List<String> scopes = normalizeScopeClaim(scopeOrNull);
            final var roleAuthorities = roles.stream().map(r -> makeAuthority("ROLE", r));
            final var groupAuthorities = groups.stream().map(g -> makeAuthority("GROUP", g));
            final var scopeAuthorities = scopes.stream().map(s -> makeAuthority("SCOPE", s));
            return Stream.concat(
                    Stream.concat(defaultAuthorities.stream(), roleAuthorities),
                    Stream.concat(groupAuthorities, scopeAuthorities)
            ).toList();
        } catch (Exception ex) {
            throw new BadCredentialsException("unparseable claims", ex);
        }
    }

    private List<String> normalizeScopeClaim(Object claim) {
        if (claim == null) {
            return List.of();
        }
        if (claim instanceof String spaceSeparetedScopes) {
            return Stream.of(spaceSeparetedScopes.split(" "))
                    .map(String::strip)
                    .filter(String::isEmpty)
                    .toList();
        }
        if (claim instanceof List scopes) {
            return scopes.stream()
                    .map(Object::toString)
                    .toList();
        }
        throw new BadCredentialsException("unparseable scope, neither a string or a list");
    }

    private static SimpleGrantedAuthority makeAuthority(String type, String value) {
        final var suffix = value.toUpperCase().replace('-', '_');
        return new SimpleGrantedAuthority(String.format("%s_%s", type.toUpperCase(), suffix));
    }

}
