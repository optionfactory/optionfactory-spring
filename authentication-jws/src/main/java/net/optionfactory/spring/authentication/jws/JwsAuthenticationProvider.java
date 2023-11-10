package net.optionfactory.spring.authentication.jws;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.authentication.bearer.token.BearerToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class JwsAuthenticationProvider implements AuthenticationProvider {

    private final byte[] key;
    private final Function<Claims, List<GrantedAuthority>> authoritiesMapper;

    public JwsAuthenticationProvider(byte[] key, Function<Claims, List<GrantedAuthority>> authoritiesMapper) {
        this.key = key;
        this.authoritiesMapper = authoritiesMapper;
    }

    private static GrantedAuthority toAuthority(String type, String value) {
        final var suffix = value.toUpperCase().replace('-', '_');
        return new SimpleGrantedAuthority(String.format("%s_%s", type.toUpperCase(), suffix));
    }

    public static List<GrantedAuthority> rolesAndGroupsFromClaims(Claims claims) {
        final List<String> rolesOrNull = claims.get("roles", List.class);
        final List<String> groupsOrNull = claims.get("groups", List.class);
        final List<String> roles = rolesOrNull != null ? rolesOrNull : List.of();
        final List<String> groups = groupsOrNull != null ? groupsOrNull : List.of();
        final var roleAuthorities = roles.stream().map(r -> toAuthority("ROLE", r));
        final var groupAuthorities = groups.stream().map(g -> toAuthority("GROUP", g));
        final var defaultAuthorities = Stream.of(new SimpleGrantedAuthority("ROLE_USER"));
        return Stream.concat(
                Stream.concat(defaultAuthorities, roleAuthorities),
                groupAuthorities
        ).collect(Collectors.toList());
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final BearerToken bearer = (BearerToken) authentication;
        final Jws<Claims> jws = Jwts.parser().setSigningKey(key).build().parseClaimsJws(bearer.getCredentials());
        final Claims principal = jws.getBody();
        final var authorities = authoritiesMapper.apply(principal);
        final var a = new JwsAuthenticatedToken(bearer.getCredentials(), principal, authorities);
        a.setDetails(bearer.getDetails());
        return a;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerToken.class.isAssignableFrom(authentication);
    }

}
