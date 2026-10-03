package net.optionfactory.spring.authentication.tokens;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import net.optionfactory.spring.authentication.tokens.jwt.ClaimsPolicy;
import net.optionfactory.spring.authentication.tokens.jwt.JweAuthenticationConfigurer;
import net.optionfactory.spring.authentication.tokens.jwt.JwsAuthenticationConfigurer;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor.JweProcessor;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor.JwsProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/// Authenticates requests by a token carried in a request header: static tokens (api keys, basic
/// credentials of a technical user) and signed or encrypted JWTs.
///
/// ```java
/// http.with(HttpHeaderAuthentication.configurer(), c -> {
///     c.bearer(System.getenv("M2M_TOKEN"), "batch", "ROLE_M2M");
///     c.basic("monitoring", System.getenv("MONITORING_PASSWORD"), "monitoring", "ROLE_MONITORING");
///     c.jws(ClaimsPolicy.issuer("https://issuer.example.com").audience("my-service"), jws -> {
///         jws.verify(issuerPublicKey);
///         jws.principal((header, claims) -> claims.getSubject());
///     });
/// });
/// ```
///
/// The configured headers and schemes are searched by a [HttpHeaderAuthenticationFilter], which
/// hands the token it finds to a [HttpHeaderAuthenticationProvider] running the configured
/// [TokenProcessor]s. A request carrying no token, or a token that is rejected, proceeds with
/// whatever authentication earlier mechanisms established, if any, leaving the decision to the
/// authorization rules and the entry point (see `UnauthorizedStatusEntryPoint`).
public class HttpHeaderAuthentication {

    /// @return a configurer to register with `HttpSecurity.with(...)`
    public static Configurer configurer() {
        return new Configurer();
    }

    /// Collects the tokens to accept, registering their header and scheme with the filter and their
    /// processors with the provider.
    ///
    /// Processors run in configuration order, except that all the `jws(...)` and `jwe(...)`
    /// configurations are gathered into one [JwtTokenProcessor] that runs after every other
    /// processor, whatever the order they were configured in.
    ///
    /// A static token is either lax or strict. A lax one (`bearer`, `token`, `basic`) authenticates
    /// a matching token and lets any other token on its header through to the next processor. A
    /// strict one (`bearerStrict`, `tokenStrict`) owns its header and scheme: any other token found
    /// there is rejected without reaching later processors, so a strict static token cannot share
    /// its header with other static tokens configured after it, nor with JWTs: such a
    /// configuration fails when the security chain is built.
    ///
    /// The filter is added before `UsernamePasswordAuthenticationFilter`, and the provider is
    /// registered with the chain's shared `AuthenticationManager`.
    public static class Configurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

        private final static String BEARER_AUTH_SCHEME = "Bearer";
        private final static String BASIC_AUTH_SCHEME = "Basic";

        private final LinkedHashSet<HeaderAndScheme> headerAndSchemes = new LinkedHashSet<>();
        private final List<TokenProcessor> processors = new ArrayList<>();
        private final List<JwsProcessor> jwsProcessors = new ArrayList<>();
        private final List<JweProcessor> jweProcessors = new ArrayList<>();

        /// Accepts a static token on `Authorization: Bearer`, letting other bearer tokens through to
        /// the next processors.
        ///
        /// @param token the expected token, compared in constant time
        /// @param principal the principal of a request carrying it
        /// @param authorities the authorities granted to it
        /// @return this configurer
        public Configurer bearer(String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            return token(HttpHeaders.AUTHORIZATION, BEARER_AUTH_SCHEME, token, principal, authorities);
        }

        /// Accepts a static token on `Authorization: Bearer`, rejecting any other bearer token
        /// before later processors see it.
        ///
        /// @param token the expected token, compared in constant time
        /// @param principal the principal of a request carrying it
        /// @param authorities the authorities granted to it
        /// @return this configurer
        public Configurer bearerStrict(String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            return tokenStrict(HttpHeaders.AUTHORIZATION, BEARER_AUTH_SCHEME, token, principal, authorities);
        }

        /// As [#bearer(String, Object, Collection)].
        ///
        /// @param token the expected token, compared in constant time
        /// @param principal the principal of a request carrying it
        /// @param authorities the names of the authorities granted to it, e.g. `ROLE_M2M`
        /// @return this configurer
        public Configurer bearer(String token, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            return bearer(token, principal, sgas);
        }

        /// As [#bearerStrict(String, Object, Collection)].
        ///
        /// @param token the expected token, compared in constant time
        /// @param principal the principal of a request carrying it
        /// @param authorities the names of the authorities granted to it, e.g. `ROLE_M2M`
        /// @return this configurer
        public Configurer bearerStrict(String token, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            return bearerStrict(token, principal, sgas);
        }

        /// Accepts a static token on any header, letting other tokens on it through to the next
        /// processors.
        ///
        /// @param headerName the header carrying the token
        /// @param authScheme the scheme prefixing the token, matched case-insensitively, or blank
        /// for a header carrying the bare token
        /// @param token the expected token, compared in constant time
        /// @param principal the principal of a request carrying it
        /// @param authorities the authorities granted to it
        /// @return this configurer
        public Configurer token(String headerName, String authScheme, String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            final var hs = new HeaderAndScheme(headerName, authScheme);
            headerAndSchemes.add(hs);
            processors.add(new TokenProcessor.StaticLax(hs, token, new PrincipalAndAuthorities(principal, authorities)));
            return this;
        }

        /// Accepts a static token on any header, rejecting any other token found there before later
        /// processors see it.
        ///
        /// @param headerName the header carrying the token
        /// @param authScheme the scheme prefixing the token, matched case-insensitively, or blank
        /// for a header carrying the bare token
        /// @param token the expected token, compared in constant time
        /// @param principal the principal of a request carrying it
        /// @param authorities the authorities granted to it
        /// @return this configurer
        public Configurer tokenStrict(String headerName, String authScheme, String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
            final var hs = new HeaderAndScheme(headerName, authScheme);
            headerAndSchemes.add(hs);
            processors.add(new TokenProcessor.StaticStrict(hs, token, new PrincipalAndAuthorities(principal, authorities)));
            return this;
        }

        /// As [#token(String, String, String, Object, Collection)].
        ///
        /// @param headerName the header carrying the token
        /// @param authScheme the scheme prefixing the token, or blank for a bare token
        /// @param token the expected token, compared in constant time
        /// @param principal the principal of a request carrying it
        /// @param authorities the names of the authorities granted to it
        /// @return this configurer
        public Configurer token(String headerName, String authScheme, String token, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            return token(headerName, authScheme, token, principal, sgas);
        }

        /// As [#tokenStrict(String, String, String, Object, Collection)].
        ///
        /// @param headerName the header carrying the token
        /// @param authScheme the scheme prefixing the token, or blank for a bare token
        /// @param token the expected token, compared in constant time
        /// @param principal the principal of a request carrying it
        /// @param authorities the names of the authorities granted to it
        /// @return this configurer
        public Configurer tokenStrict(String headerName, String authScheme, String token, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            return tokenStrict(headerName, authScheme, token, principal, sgas);
        }

        /// Accepts static `Authorization: Basic` credentials, letting other basic credentials through
        /// to the next processors.
        ///
        /// The credentials are compared in their encoded form, the base64 of the UTF-8
        /// `username:password`, so both parts are case-sensitive and must be encoded by the client
        /// exactly that way.
        ///
        /// @param username the expected username
        /// @param password the expected password
        /// @param principal the principal of a request carrying them
        /// @param authorities the authorities granted to it
        /// @return this configurer
        public Configurer basic(String username, String password, Object principal, Collection<? extends GrantedAuthority> authorities) {
            var encodedValue = Base64.getEncoder().encodeToString("%s:%s".formatted(username, password).getBytes(StandardCharsets.UTF_8));
            return token(HttpHeaders.AUTHORIZATION, BASIC_AUTH_SCHEME, encodedValue, principal, authorities);
        }

        /// As [#basic(String, String, Object, Collection)].
        ///
        /// @param username the expected username
        /// @param password the expected password
        /// @param principal the principal of a request carrying them
        /// @param authorities the names of the authorities granted to it
        /// @return this configurer
        public Configurer basic(String username, String password, Object principal, String... authorities) {
            final var sgas = Stream.of(authorities).map(SimpleGrantedAuthority::new).toList();
            return basic(username, password, principal, sgas);
        }

        /// Adds a custom processor for the tokens found on a header and scheme, which is registered
        /// with the filter like those of the other methods. The processor only sees the tokens found
        /// there: tokens on any other header and scheme are passed on without consulting it.
        ///
        /// @param headerName the header carrying the token
        /// @param authScheme the scheme preceding the token, compared case-insensitively; blank
        /// for a header carrying the token alone
        /// @param processor the processor to add, run after the processors configured before it
        /// @return this configurer
        public Configurer processor(String headerName, String authScheme, TokenProcessor processor) {
            final var hs = new HeaderAndScheme(headerName, authScheme);
            headerAndSchemes.add(hs);
            processors.add(new TokenProcessor.Custom(hs, processor));
            return this;
        }

        /// Accepts signed JWTs whose claims satisfy the given policy.
        ///
        /// @param claims the claims a token must carry to be accepted
        /// @param customizer configures how the token is found, verified and turned into a principal
        /// @return this configurer
        public Configurer jws(ClaimsPolicy claims, Customizer<JwsAuthenticationConfigurer> customizer) {
            final var builder = JwsAuthenticationConfigurer.builder(claims);
            customizer.customize(builder);
            final var processor = builder.build();
            jwsProcessors.add(processor);
            headerAndSchemes.add(processor.hs());
            return this;
        }

        /// Accepts encrypted JWTs whose claims satisfy the given policy.
        ///
        /// @param claims the claims a token must carry to be accepted
        /// @param customizer configures how the token is found, decrypted and turned into a principal
        /// @return this configurer
        public Configurer jwe(ClaimsPolicy claims, Customizer<JweAuthenticationConfigurer> customizer) {
            final var builder = JweAuthenticationConfigurer.builder(claims);
            customizer.customize(builder);
            final var processor = builder.build();
            jweProcessors.add(processor);
            headerAndSchemes.add(processor.hs());
            return this;
        }

        /// The processors in the order they are consulted: the static and custom ones in
        /// configuration order, then a single [JwtTokenProcessor] gathering every `jws` and `jwe`
        /// configuration.
        ///
        /// A strict static token rejects every other token on its header and scheme, and a
        /// rejection ends the search: a static token configured after it on the same header and
        /// scheme, and any `jws` or `jwe` configuration matching them, would never see a token.
        /// Such a configuration is rejected here, when the security chain is built, rather than
        /// left to silently reject every one of those tokens. Custom processors are checked too, on
        /// the header and scheme they are registered with.
        ///
        /// @return the processors, in the order they are consulted
        /// @throws IllegalStateException when a strict static token shadows another configuration
        List<TokenProcessor> tokenProcessors() {
            final var strict = new LinkedHashSet<HeaderAndScheme>();
            for (final var processor : processors) {
                final HeaderAndScheme hs = processor instanceof TokenProcessor.StaticLax lax ? lax.hs
                        : processor instanceof TokenProcessor.StaticStrict ss ? ss.hs
                        : processor instanceof TokenProcessor.Custom custom ? custom.hs()
                        : null;
                if (hs != null && strict.contains(hs)) {
                    throw new IllegalStateException(String.format("a static token on %s is configured after a strict one on the same header and scheme, which rejects it", describe(hs)));
                }
                if (processor instanceof TokenProcessor.StaticStrict ss) {
                    strict.add(ss.hs);
                }
            }
            Stream.concat(jwsProcessors.stream().map(JwsProcessor::hs), jweProcessors.stream().map(JweProcessor::hs))
                    .filter(strict::contains)
                    .findFirst()
                    .ifPresent(hs -> {
                        throw new IllegalStateException(String.format("a jws or jwe configuration on %s is shadowed by a strict static token on the same header and scheme, which rejects every other token there", describe(hs)));
                    });
            if (jweProcessors.isEmpty() && jwsProcessors.isEmpty()) {
                return processors;
            }
            final var jwt = new JwtTokenProcessor(jwsProcessors, jweProcessors);
            return Stream.concat(processors.stream(), Stream.of(jwt)).toList();
        }

        private static String describe(HeaderAndScheme hs) {
            return hs.scheme().isEmpty() ? String.format("header %s", hs.header()) : String.format("header %s, scheme %s", hs.header(), hs.scheme().trim());
        }

        /// Registers the [HttpHeaderAuthenticationProvider]. It happens at init time because
        /// `HttpSecurity` builds the shared `AuthenticationManager` after init and before configure:
        /// a configure-time registration only worked because `ProviderManager` kept the builder's
        /// live provider list by reference.
        ///
        /// @param http the security being built
        @Override
        public void init(HttpSecurity http) {
            http.authenticationProvider(new HttpHeaderAuthenticationProvider(tokenProcessors()));
        }

        /// Adds the [HttpHeaderAuthenticationFilter], searching every configured header and scheme,
        /// before `UsernamePasswordAuthenticationFilter`.
        ///
        /// The filter sets the authentication on the same `SecurityContextHolderStrategy` spring
        /// security's own filters use: the chain's shared one if any, else the application
        /// context's `SecurityContextHolderStrategy` bean if there is exactly one, else
        /// `SecurityContextHolder`'s.
        ///
        /// @param http the security being built
        @Override
        public void configure(HttpSecurity http) {
            final var authenticationManager = http.getSharedObject(AuthenticationManager.class);
            final var filter = new HttpHeaderAuthenticationFilter(authenticationManager, headerAndSchemes);
            filter.setSecurityContextHolderStrategy(securityContextHolderStrategy(http));
            postProcess(filter);
            http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        }

        private static SecurityContextHolderStrategy securityContextHolderStrategy(HttpSecurity http) {
            final var shared = http.getSharedObject(SecurityContextHolderStrategy.class);
            if (shared != null) {
                return shared;
            }
            final var context = http.getSharedObject(ApplicationContext.class);
            if (context == null) {
                return SecurityContextHolder.getContextHolderStrategy();
            }
            return context.getBeanProvider(SecurityContextHolderStrategy.class).getIfUnique(SecurityContextHolder::getContextHolderStrategy);
        }

    }

    /// What a [TokenProcessor] grants to the request carrying a token it accepts.
    ///
    /// @param principal the principal of the authenticated request
    /// @param authorities its authorities
    public record PrincipalAndAuthorities(Object principal, Collection<? extends GrantedAuthority> authorities) {

    }

    /// A token found in a request and not yet authenticated.
    ///
    /// Its principal is where the token was found, the header and its scheme, never the token itself:
    /// the token is a secret, and a principal is not treated as one. It shows up in `toString()` and in
    /// `getName()`, both of which spring and applications log, and in the failure events an audit
    /// listener records. The token is only available as the credentials, which are masked.
    public static class UnauthenticatedToken extends AbstractAuthenticationToken {

        private final HeaderAndScheme hs;
        private final String token;

        /// @param hs where the token was found
        /// @param token the token, without the scheme
        /// @param request the request carrying it, whose remote address and session id become the
        /// details
        public UnauthenticatedToken(HeaderAndScheme hs, String token, HttpServletRequest request) {
            super((Collection<? extends GrantedAuthority>)null);
            this.hs = hs;
            this.token = token;
            super.setAuthenticated(false);
            super.setDetails(new WebAuthenticationDetails(request));
        }

        /// @return the token, without the scheme
        @Override
        public String getCredentials() {
            return token;
        }

        /// @return where the token was found, never the token itself
        @Override
        public HeaderAndScheme getPrincipal() {
            return hs;
        }

        /// @return where the token was found
        public HeaderAndScheme getHeaderAndScheme() {
            return hs;
        }
    }

    /// A token a [TokenProcessor] accepted: the authentication a request carrying it runs with.
    ///
    /// The token stays available as the credentials; `toString()` masks them.
    public static class AuthenticatedToken extends AbstractAuthenticationToken {

        private final String token;
        private final Object principal;

        /// @param token the accepted token
        /// @param principal the principal the processor granted
        /// @param details the details of the [UnauthenticatedToken] it was found as
        /// @param authorities the authorities the processor granted
        public AuthenticatedToken(String token, Object principal, Object details, Collection<? extends GrantedAuthority> authorities) {
            super(authorities);
            this.token = token;
            this.principal = principal;
            super.setDetails(details);
            super.setAuthenticated(true);
        }

        /// @return the accepted token
        @Override
        public String getCredentials() {
            return token;
        }

        /// @return the principal the processor granted
        @Override
        public Object getPrincipal() {
            return principal;
        }
    }

    /// Decides whether a token found in a request is accepted, and what it grants.
    ///
    /// [HttpHeaderAuthenticationProvider] runs its processors in order until one accepts the token.
    /// A processor has three answers: a [PrincipalAndAuthorities] accepts the token, `null` leaves
    /// it to the next processor, and an `AuthenticationException` (usually a
    /// `BadCredentialsException`) rejects it outright, with no later processor consulted.
    public interface TokenProcessor {

        /// @param hs the header and scheme the token was found on
        /// @param token the token, without the scheme
        /// @return what the token grants, or `null` when this processor does not accept it and
        /// leaves it to the next one
        /// @throws org.springframework.security.core.AuthenticationException to reject the token
        /// without consulting later processors
        HttpHeaderAuthentication.PrincipalAndAuthorities process(HeaderAndScheme hs, String token);

        /// Accepts a static token on a given header and scheme, leaving any other token to the next
        /// processor. The token is compared in constant time.
        public static class StaticLax implements TokenProcessor {

            private final HeaderAndScheme hs;
            private final String token;
            private final HttpHeaderAuthentication.PrincipalAndAuthorities paa;

            /// @param hs the header and scheme the token must be found on
            /// @param token the expected token
            /// @param paa what the token grants
            public StaticLax(HeaderAndScheme hs, String token, PrincipalAndAuthorities paa) {
                this.hs = hs;
                this.token = token;
                this.paa = paa;
            }

            /// @return what the token grants when both the header and scheme and the token match,
            /// `null` otherwise
            @Override
            public HttpHeaderAuthentication.PrincipalAndAuthorities process(HeaderAndScheme hs, String token) {
                return this.hs.equals(hs) && constantTimeEquals(this.token, token) ? paa : null;
            }
        }

        /// Accepts a static token on a given header and scheme, and rejects any other token found
        /// there: the header and scheme belong to this token alone. Tokens on other headers are left
        /// to the next processor. The token is compared in constant time.
        /// A custom processor, consulted only for the tokens found on its own header and scheme.
        ///
        /// @param hs the header and scheme the processor was registered with
        /// @param delegate the processor
        public record Custom(HeaderAndScheme hs, TokenProcessor delegate) implements TokenProcessor {

            /// @param hs the header and scheme the token was found on
            /// @param token the token
            /// @return what the delegate returns for a token on its own header and scheme, `null`
            /// for any other token
            @Override
            public HttpHeaderAuthentication.PrincipalAndAuthorities process(HeaderAndScheme hs, String token) {
                return this.hs.equals(hs) ? delegate.process(hs, token) : null;
            }
        }

        public static class StaticStrict implements TokenProcessor {

            private final HeaderAndScheme hs;
            private final String token;
            private final HttpHeaderAuthentication.PrincipalAndAuthorities paa;

            /// @param hs the header and scheme the token must be found on
            /// @param token the expected token
            /// @param paa what the token grants
            public StaticStrict(HeaderAndScheme hs, String token, HttpHeaderAuthentication.PrincipalAndAuthorities paa) {
                this.hs = hs;
                this.token = token;
                this.paa = paa;
            }

            /// @return what the token grants when it matches, `null` when it was found on another
            /// header or scheme
            /// @throws BadCredentialsException when it was found on this header and scheme but does
            /// not match
            @Override
            public HttpHeaderAuthentication.PrincipalAndAuthorities process(HeaderAndScheme hs, String token) {
                if (!this.hs.equals(hs)) {
                    return null;
                }
                if (!constantTimeEquals(this.token, token)) {
                    throw new BadCredentialsException("unknown token");
                }
                return paa;
            }
        }

    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

}
