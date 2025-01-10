package net.optionfactory.spring.authentication.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParserBuilder;
import jakarta.servlet.http.HttpServletRequest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import net.optionfactory.spring.authentication.token.jwt.JwtAuthenticationProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

public class HttpHeaderAuthentication {

    public static Configurer configurer() {
        return new Configurer();
    }

    /**
     * Configures the {@link HttpHeaderAuthenticationFilter} by injecting the
     * Spring managed {@link AuthenticationManager} and registers it with the
     * HTTP security.
     */
    public static class Configurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

        private String headerName = HttpHeaders.AUTHORIZATION;
        private String authScheme = "BEARER ";
        private final List<TokenProcessor> processors = new ArrayList<>();
        private final List<AuthenticationProvider> authProviders = new ArrayList<>();

        public Configurer header(String key) {
            this.headerName = key;
            return this;
        }

        public Configurer authScheme(String prefix) {
            final var upper = prefix.toUpperCase();
            final var withSeparator = upper.endsWith(" ") ? upper : upper + " ";
            this.authScheme = withSeparator;
            return this;
        }

        public Configurer token(String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            processors.add(new TokenProcessor.Static(token, new PrincipalAndAuthorities(principal, authorities)));
            return this;
        }

        public Configurer token(String token, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            processors.add(new TokenProcessor.Static(token, new PrincipalAndAuthorities(principal, sgas)));
            return this;
        }

        public Configurer processor(TokenProcessor processor) {
            processors.add(processor);
            return this;
        }

        public Configurer authenticationProvider(AuthenticationProvider ap) {
            authProviders.add(ap);
            return this;
        }

        public Configurer jws(SecretKey key, Customizer<JwtParserBuilder> jwtParserCustomizer) {
            authProviders.add(JwtAuthenticationProvider.symmetricJws(key, JwtAuthenticationProvider::rolesAndGroupsFromClaims, jwtParserCustomizer));
            return this;
        }

        public Configurer jws(PublicKey key, Customizer<JwtParserBuilder> jwtParserCustomizer) {
            authProviders.add(JwtAuthenticationProvider.asymmetricJws(key, JwtAuthenticationProvider::rolesAndGroupsFromClaims, jwtParserCustomizer));
            return this;
        }

        public Configurer jws(SecretKey key, Function<Claims, Collection<? extends GrantedAuthority>> authoritiesMapper, Customizer<JwtParserBuilder> jwtParserCustomizer) {
            authProviders.add(JwtAuthenticationProvider.symmetricJws(key, authoritiesMapper, jwtParserCustomizer));
            return this;
        }

        public Configurer jws(PublicKey key, Function<Claims, Collection<? extends GrantedAuthority>> authoritiesMapper, Customizer<JwtParserBuilder> jwtParserCustomizer) {
            authProviders.add(JwtAuthenticationProvider.asymmetricJws(key, authoritiesMapper, jwtParserCustomizer));
            return this;
        }

        public Configurer jwe(SecretKey key, Customizer<JwtParserBuilder> jwtParserCustomizer) {
            authProviders.add(JwtAuthenticationProvider.symmetricJwe(key, JwtAuthenticationProvider::rolesAndGroupsFromClaims, jwtParserCustomizer));
            return this;
        }

        public Configurer jwe(PrivateKey key, Customizer<JwtParserBuilder> jwtParserCustomizer) {
            authProviders.add(JwtAuthenticationProvider.asymmetricJwe(key, JwtAuthenticationProvider::rolesAndGroupsFromClaims, jwtParserCustomizer));
            return this;
        }

        public Configurer jwe(SecretKey key, Function<Claims, Collection<? extends GrantedAuthority>> authoritiesMapper, Customizer<JwtParserBuilder> jwtParserCustomizer) {
            authProviders.add(JwtAuthenticationProvider.symmetricJwe(key, authoritiesMapper, jwtParserCustomizer));
            return this;
        }

        public Configurer jwe(PrivateKey key, Function<Claims, Collection<? extends GrantedAuthority>> authoritiesMapper, Customizer<JwtParserBuilder> jwtParserCustomizer) {
            authProviders.add(JwtAuthenticationProvider.asymmetricJwe(key, authoritiesMapper, jwtParserCustomizer));
            return this;
        }

        @Override
        public void configure(HttpSecurity http) {
            final AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
            final HttpHeaderAuthenticationFilter filter = new HttpHeaderAuthenticationFilter(authenticationManager, headerName, authScheme);
            postProcess(filter);
            if (!processors.isEmpty()) {
                http.authenticationProvider(new HttpHeaderAuthenticationProvider(processors));
            }
            for (final var authProvider : authProviders) {
                http.authenticationProvider(authProvider);
            }
            http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        }
    }

    public record PrincipalAndAuthorities(Object principal, Collection<? extends GrantedAuthority> authorities) {

    }

    public interface TokenProcessor {

        PrincipalAndAuthorities process(String token);

        public static class Static implements TokenProcessor {

            private final String token;
            private final PrincipalAndAuthorities paa;

            public Static(String token, PrincipalAndAuthorities paa) {
                this.token = token;
                this.paa = paa;
            }

            @Override
            public PrincipalAndAuthorities process(String token) {
                return this.token.equals(token) ? paa : null;
            }

        }
    }

    public static class UnauthenticatedToken extends AbstractAuthenticationToken {

        private final String token;

        public UnauthenticatedToken(String token, HttpServletRequest request) {
            super(null);
            this.token = token;
            super.setAuthenticated(false);
            super.setDetails(new WebAuthenticationDetails(request));
        }

        @Override
        public String getCredentials() {
            return token;
        }

        @Override
        public String getPrincipal() {
            return token;
        }
    }

    public static class AuthenticatedToken extends AbstractAuthenticationToken {

        private final String token;
        private final Object principal;

        public AuthenticatedToken(String token, Object principal, Object details, Collection<? extends GrantedAuthority> authorities) {
            super(authorities);
            this.token = token;
            this.principal = principal;
            super.setDetails(details);
            super.setAuthenticated(true);
        }

        @Override
        public String getCredentials() {
            return token;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }
    }
}
