package net.optionfactory.spring.authentication.tokens;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.*;
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

        private final static String BEARER_AUTH_SCHEME = "Bearer";
        private final static String BASIC_AUTH_SCHEME = "Basic";

        private final LinkedHashSet<TokenSelector> tokenSelectors = new LinkedHashSet<>();
        private final List<TokenProcessor> processors = new ArrayList<>();
        private final List<JwsProcessor> jwsProcessors = new ArrayList<>();
        private final List<JweProcessor> jweProcessors = new ArrayList<>();

        /**
         * @deprecated Use {@link #bearer(String, Object, Collection)} instead.
         */
        @Deprecated(forRemoval = true)
        public Configurer token(String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            return bearer(token, principal, authorities);
        }

        public Configurer bearer(String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            return token(HttpHeaders.AUTHORIZATION, BEARER_AUTH_SCHEME, token, principal, authorities);
        }

        /**
         * @deprecated Use {@link #bearerStrict(String, Object, Collection)} instead.
         */
        @Deprecated(forRemoval = true)
        public Configurer tokenStrict(String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            return bearerStrict(token, principal, authorities);
        }

        public Configurer bearerStrict(String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            return tokenStrict(HttpHeaders.AUTHORIZATION, BEARER_AUTH_SCHEME, token, principal, authorities);
        }

        /**
         * @deprecated Use {@link #bearer(String, Object, String...)} instead.
         */
        @Deprecated(forRemoval = true)
        public Configurer token(String token, Object principal, String... authorities) {
            return bearer(token, principal, authorities);
        }

        public Configurer bearer(String token, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            return bearer(token, principal, sgas);
        }

        /**
         * @deprecated Use {@link #bearerStrict(String, Object, String...)} instead.
         */
        @Deprecated(forRemoval = true)
        public Configurer tokenStrict(String token, Object principal, String... authorities) {
            return bearerStrict(token, principal, authorities);
        }

        public Configurer bearerStrict(String token, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            return bearerStrict(token, principal, sgas);
        }

        public Configurer token(String headerName, String authScheme, String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            authScheme = authScheme.toUpperCase().trim() + " ";
            var headerSelector = new TokenSelector(headerName, authScheme);
            tokenSelectors.add(headerSelector);
            processors.add(new TokenProcessor.StaticLax(headerSelector, token, new PrincipalAndAuthorities(principal, authorities)));
            return this;
        }

        public Configurer tokenStrict(String headerName, String authScheme, String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            authScheme = authScheme.toUpperCase().trim() + " ";
            var headerSelector = new TokenSelector(headerName, authScheme);
            tokenSelectors.add(headerSelector);
            processors.add(new TokenProcessor.StaticStrict(headerSelector, token, new PrincipalAndAuthorities(principal, authorities)));
            return this;
        }

        public Configurer token(String headerName, String authScheme, String token, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            return token(headerName, authScheme, token, principal, sgas);
        }

        public Configurer tokenStrict(String headerName, String authScheme, String token, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            return tokenStrict(headerName, authScheme, token, principal, sgas);
        }

        public Configurer basic(String username, String password, Object principal, Collection<? extends GrantedAuthority> authorities) {
            var encodedValue = Base64.getEncoder().encodeToString("%s:%s".formatted(username, password).getBytes(StandardCharsets.UTF_8));
            return token(HttpHeaders.AUTHORIZATION, BASIC_AUTH_SCHEME, encodedValue, principal, authorities);
        }

        public Configurer basic(String username, String password, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            return basic(username, password, principal, sgas);
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
            final var filter = new HttpHeaderAuthenticationFilter(authenticationManager, tokenSelectors);
            postProcess(filter);
            http.authenticationProvider(new HttpHeaderAuthenticationProvider(makeProcessors(processors, jwsProcessors, jweProcessors)));
            http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        }

    }

    public record PrincipalAndAuthorities(Object principal, Collection<? extends GrantedAuthority> authorities) {

    }

    public static class UnauthenticatedToken extends AbstractAuthenticationToken {

        private final TokenSelector tokenSelector;
        private final String token;

        public UnauthenticatedToken(TokenSelector tokenSelector, String token, HttpServletRequest request) {
            super(null);
            this.tokenSelector = tokenSelector;
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

        public TokenSelector getTokenSelector() {
            return tokenSelector;
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

        HttpHeaderAuthentication.PrincipalAndAuthorities process(TokenSelector tokenSelector, String token);
        public static class StaticLax implements TokenProcessor {

            private final TokenSelector tokenSelector;
            private final String token;
            private final HttpHeaderAuthentication.PrincipalAndAuthorities paa;

            public StaticLax(TokenSelector tokenSelector, String token, PrincipalAndAuthorities paa) {
                this.tokenSelector = tokenSelector;
                this.token = token;
                this.paa = paa;
            }

            @Override
            public HttpHeaderAuthentication.PrincipalAndAuthorities process(TokenSelector tokenSelector, String token) {
                return this.tokenSelector.equals(tokenSelector) && this.token.equals(token) ? paa : null;
            }
        }

        public static class StaticStrict implements TokenProcessor {

            private final TokenSelector tokenSelector;
            private final String token;
            private final HttpHeaderAuthentication.PrincipalAndAuthorities paa;

            public StaticStrict(TokenSelector tokenSelector, String token, HttpHeaderAuthentication.PrincipalAndAuthorities paa) {
                this.tokenSelector = tokenSelector;
                this.token = token;
                this.paa = paa;
            }

            @Override
            public HttpHeaderAuthentication.PrincipalAndAuthorities process(TokenSelector tokenSelector, String token) {
                if (this.tokenSelector.equals(tokenSelector) && !this.token.equals(token)) {
                    throw new BadCredentialsException("unknown token");
                }
                return paa;
            }
        }

    }

}
