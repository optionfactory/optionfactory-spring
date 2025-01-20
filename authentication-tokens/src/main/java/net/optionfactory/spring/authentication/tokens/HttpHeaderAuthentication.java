package net.optionfactory.spring.authentication.tokens;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import net.optionfactory.spring.authentication.tokens.jwt.JweAuthenticationConfigurer;
import net.optionfactory.spring.authentication.tokens.jwt.JwsAuthenticationConfigurer;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor.JweProcessor;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor.JwsProcessor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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
        private String authScheme = "Bearer";
        private final List<TokenProcessor> processors = new ArrayList<>();
        private final List<JwsProcessor> jwsProcessors = new ArrayList<>();
        private final List<JweProcessor> jweProcessors = new ArrayList<>();

        public Configurer header(String key) {
            this.headerName = key;
            return this;
        }

        public Configurer authScheme(String authScheme) {
            this.authScheme = authScheme;
            return this;
        }

        public Configurer token(String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            processors.add(new TokenProcessor.StaticLax(token, new PrincipalAndAuthorities(principal, authorities)));
            return this;
        }

        public Configurer tokenStrict(String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            processors.add(new TokenProcessor.StaticStrict(token, new PrincipalAndAuthorities(principal, authorities)));
            return this;
        }

        public Configurer token(String token, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            processors.add(new TokenProcessor.StaticLax(token, new PrincipalAndAuthorities(principal, sgas)));
            return this;
        }

        public Configurer tokenStrict(String token, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            processors.add(new TokenProcessor.StaticStrict(token, new PrincipalAndAuthorities(principal, sgas)));
            return this;
        }

        public Configurer processor(TokenProcessor processor) {
            processors.add(processor);
            return this;
        }

        public Configurer jws(Customizer<JwsAuthenticationConfigurer> customizer) {
            final var builder = JwsAuthenticationConfigurer.builder();
            customizer.customize(builder);
            jwsProcessors.add(builder.build());
            return this;
        }

        public Configurer jwe(Customizer<JweAuthenticationConfigurer> customizer) {
            final var builder = JweAuthenticationConfigurer.builder();
            customizer.customize(builder);
            jweProcessors.add(builder.build());
            return this;
        }

        private static List<TokenProcessor> makeProcessors(List<TokenProcessor> processors, List<JwsProcessor> jwsProcessors, List<JweProcessor> jweProcessors) {
            if (jweProcessors.isEmpty() && jwsProcessors.isEmpty()) {
                return processors;
            }
            final var jwt = new JwtTokenProcessor(jwsProcessors, jweProcessors);
            return Stream.concat(processors.stream(), Stream.of(jwt)).toList();
        }

        @Override
        public void configure(HttpSecurity http) {
            final var authenticationManager = http.getSharedObject(AuthenticationManager.class);
            final var filter = new HttpHeaderAuthenticationFilter(authenticationManager, headerName, authScheme);
            postProcess(filter);
            http.authenticationProvider(new HttpHeaderAuthenticationProvider(makeProcessors(processors, jwsProcessors, jweProcessors)));
            http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        }

    }

    public record PrincipalAndAuthorities(Object principal, Collection<? extends GrantedAuthority> authorities) {

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

    public interface TokenProcessor {

        HttpHeaderAuthentication.PrincipalAndAuthorities process(String token);

        public static class StaticLax implements TokenProcessor {

            private final String token;
            private final HttpHeaderAuthentication.PrincipalAndAuthorities paa;

            public StaticLax(String token, HttpHeaderAuthentication.PrincipalAndAuthorities paa) {
                this.token = token;
                this.paa = paa;
            }

            @Override
            public HttpHeaderAuthentication.PrincipalAndAuthorities process(String token) {
                return this.token.equals(token) ? paa : null;
            }
        }

        public static class StaticStrict implements TokenProcessor {

            private final String token;
            private final HttpHeaderAuthentication.PrincipalAndAuthorities paa;

            public StaticStrict(String token, HttpHeaderAuthentication.PrincipalAndAuthorities paa) {
                this.token = token;
                this.paa = paa;
            }

            @Override
            public HttpHeaderAuthentication.PrincipalAndAuthorities process(String token) {
                if (!this.token.equals(token)) {
                    throw new BadCredentialsException("unknown token");
                }
                return paa;
            }
        }

    }

}
