package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class RolesGroupsAndScopesFromClaimsTest {

    private static Set<String> authorityNames(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    public void spaceSeparatedScopesAreConvertedToAuthorities() {
        final var claims = new JWTClaimsSet.Builder()
                .claim("scope", "read write admin")
                .build();
        final var converter = new RolesGroupsAndScopesFromClaims(List.of());
        final var authorities = authorityNames(converter.convert(null, claims));
        Assertions.assertEquals(Set.of("SCOPE_READ", "SCOPE_WRITE", "SCOPE_ADMIN"), authorities, "each space-separated scope becomes a SCOPE_ authority");
    }

    @Test
    public void aScopeListIsAccepted() {
        final var claims = new JWTClaimsSet.Builder()
                .claim("scope", List.of("read", "write"))
                .build();
        final var authorities = authorityNames(new RolesGroupsAndScopesFromClaims(List.of()).convert(null, claims));
        Assertions.assertEquals(Set.of("SCOPE_READ", "SCOPE_WRITE"), authorities, "a scope claim may also be a list");
    }

    @Test
    public void rolesAndGroupsArePrefixedUpperCasedAndUnderscored() {
        final var claims = new JWTClaimsSet.Builder()
                .claim("roles", List.of("admin", "read-only"))
                .claim("groups", List.of("sales-team"))
                .build();
        final var authorities = authorityNames(new RolesGroupsAndScopesFromClaims(List.of()).convert(null, claims));
        Assertions.assertEquals(Set.of("ROLE_ADMIN", "ROLE_READ_ONLY", "GROUP_SALES_TEAM"), authorities, "values are prefixed by their kind, upper-cased and with dashes turned into underscores");
    }

    @Test
    public void theDefaultAuthoritiesComeFirst() {
        final var claims = new JWTClaimsSet.Builder()
                .claim("roles", List.of("admin"))
                .build();
        final var authorities = new RolesGroupsAndScopesFromClaims(List.of(new SimpleGrantedAuthority("ROLE_M2M"))).convert(null, claims)
                .stream().map(GrantedAuthority::getAuthority).toList();
        Assertions.assertEquals(List.of("ROLE_M2M", "ROLE_ADMIN"), authorities, "the default authorities are granted first, then those from the claims");
    }

    @Test
    public void aTokenWithoutClaimsGetsTheDefaultAuthoritiesOnly() {
        final var authorities = authorityNames(new RolesGroupsAndScopesFromClaims(List.of(new SimpleGrantedAuthority("ROLE_M2M"))).convert(null, new JWTClaimsSet.Builder().build()));
        Assertions.assertEquals(Set.of("ROLE_M2M"), authorities, "missing roles, groups and scope claims grant nothing");
    }

    @Test
    public void rolesThatAreNotAListAreRejected() {
        final var claims = new JWTClaimsSet.Builder()
                .claim("roles", "admin")
                .build();
        final var converter = new RolesGroupsAndScopesFromClaims(List.of());
        Assertions.assertThrows(BadCredentialsException.class, () -> converter.convert(null, claims), "a roles claim must be a list of strings");
    }

    @Test
    public void aScopeThatIsNeitherAStringNorAListIsRejected() {
        final var claims = new JWTClaimsSet.Builder()
                .claim("scope", 42)
                .build();
        final var converter = new RolesGroupsAndScopesFromClaims(List.of());
        Assertions.assertThrows(BadCredentialsException.class, () -> converter.convert(null, claims), "a numeric scope claim cannot be read");
    }

    @Test
    public void authoritiesAreUpperCasedIndependentlyOfTheDefaultLocale() {
        final Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            final var claims = new JWTClaimsSet.Builder()
                    .claim("roles", List.of("admin"))
                    .build();
            final var authorities = authorityNames(new RolesGroupsAndScopesFromClaims(List.of()).convert(null, claims));
            Assertions.assertEquals(Set.of("ROLE_ADMIN"), authorities, "a turkish default locale must not turn 'i' into a dotted capital I");
        } finally {
            Locale.setDefault(original);
        }
    }
}
