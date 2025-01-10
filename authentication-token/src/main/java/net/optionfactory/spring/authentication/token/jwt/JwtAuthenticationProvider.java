package net.optionfactory.spring.authentication.token.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import net.optionfactory.spring.authentication.token.HttpHeaderAuthentication;
import net.optionfactory.spring.authentication.token.HttpHeaderAuthentication.AuthenticatedToken;
import net.optionfactory.spring.authentication.token.HttpHeaderAuthentication.UnauthenticatedToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class JwtAuthenticationProvider implements AuthenticationProvider {

    public enum Type {
        JWS, JWE;
    }
    private final Type type;
    private final Function<Claims, Collection<? extends GrantedAuthority>> authoritiesMapper;
    private final JwtParser parser;

    public JwtAuthenticationProvider(Type type, JwtParser parser, Function<Claims, Collection<? extends GrantedAuthority>> authoritiesMapper) {
        this.type = type;
        this.authoritiesMapper = authoritiesMapper;
        this.parser = parser;
    }

    public static JwtAuthenticationProvider symmetricJws(SecretKey key, Function<Claims, Collection<? extends GrantedAuthority>> authoritiesMapper, Customizer<JwtParserBuilder> jwtParserCustomizer) {
        final var pb = Jwts.parser().verifyWith(key);
        jwtParserCustomizer.customize(pb);
        return new JwtAuthenticationProvider(Type.JWS, pb.build(), authoritiesMapper);
    }

    public static JwtAuthenticationProvider asymmetricJws(PublicKey key, Function<Claims, Collection<? extends GrantedAuthority>> authoritiesMapper, Customizer<JwtParserBuilder> jwtParserCustomizer) {
        final var pb = Jwts.parser().verifyWith(key);
        jwtParserCustomizer.customize(pb);
        return new JwtAuthenticationProvider(Type.JWS, pb.build(), authoritiesMapper);
    }

    public static JwtAuthenticationProvider symmetricJwe(SecretKey key, Function<Claims, Collection<? extends GrantedAuthority>> authoritiesMapper, Customizer<JwtParserBuilder> jwtParserCustomizer) {
        final var pb = Jwts.parser().decryptWith(key);
        jwtParserCustomizer.customize(pb);
        return new JwtAuthenticationProvider(Type.JWE, pb.build(), authoritiesMapper);
    }

    public static JwtAuthenticationProvider asymmetricJwe(PrivateKey key, Function<Claims, Collection<? extends GrantedAuthority>> authoritiesMapper, Customizer<JwtParserBuilder> jwtParserCustomizer) {
        final var pb = Jwts.parser().decryptWith(key);
        jwtParserCustomizer.customize(pb);
        return new JwtAuthenticationProvider(Type.JWE, pb.build(), authoritiesMapper);
    }

    private static SimpleGrantedAuthority toAuthority(String type, String value) {
        final var suffix = value.toUpperCase().replace('-', '_');
        return new SimpleGrantedAuthority(String.format("%s_%s", type.toUpperCase(), suffix));
    }

    @SuppressWarnings("unchecked")
    public static List<SimpleGrantedAuthority> rolesAndGroupsFromClaims(Claims claims) {
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
        ).toList();
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final var bearer = (HttpHeaderAuthentication.UnauthenticatedToken) authentication;
        final Claims claims;
        try {
            claims = type == Type.JWS
                    ? parser.parseSignedClaims(bearer.getCredentials()).getPayload()
                    : parser.parseEncryptedClaims(bearer.getCredentials()).getPayload();
        } catch (JwtException ex) {
            return null;
        }
        final var authorities = authoritiesMapper.apply(claims);
        return new AuthenticatedToken(bearer.getCredentials(), claims, bearer.getDetails(), authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UnauthenticatedToken.class.isAssignableFrom(authentication);
    }

}
