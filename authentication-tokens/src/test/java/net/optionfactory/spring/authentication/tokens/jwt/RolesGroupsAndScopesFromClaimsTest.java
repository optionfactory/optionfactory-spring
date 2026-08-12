package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

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
        Assertions.assertEquals(Set.of("SCOPE_READ", "SCOPE_WRITE", "SCOPE_ADMIN"), authorities);
    }
}
