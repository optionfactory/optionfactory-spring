package net.optionfactory.spring.authentication;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.session.SessionManagementFilter;

/// Entry point for principal coalescing: an application that authenticates callers in several
/// ways (form login, an authorization code login, static or jwt tokens, a resource server) sees
/// each one surface its own principal type. Coalescing maps all of them to the application's own
/// principal type, so that controllers and services can rely on a single type, e.g. with
/// `@AuthenticationPrincipal AppUser user`.
///
/// ```java
/// http.with(Principals.coalescing(AppUser.class), c -> {
///     c.principal(UserDetails.class, (auth, user) -> users.byUsername(user.getUsername()));
///     c.principal(OidcUser.class, (auth, user) -> users.bySubject(user.getSubject()));
///     c.principal("anonymousUser", AppUser.GUEST);
/// });
/// ```
///
/// See [AuthenticationsCoalescingFilter] for how a request's authentication is replaced.
public class Principals {

    /// @param <T> the application principal type
    /// @param principalType the application principal type: principals already of this type are
    /// left as they are
    /// @return a configurer to register with `HttpSecurity.with(...)`
    public static <T> PrincipalsConfigurer<T> coalescing(Class<T> principalType) {
        return new PrincipalsConfigurer<>(principalType);
    }

    /// Collects the principal mappings and installs an [AuthenticationsCoalescingFilter] in the
    /// security filter chain.
    ///
    /// Mappings are consulted in registration order, and the first one supporting a principal
    /// maps it. The filter is added before `SessionManagementFilter`, so after every
    /// authentication filter and the anonymous one. It uses the chain's shared
    /// `SecurityContextRepository` and `AuthenticationTrustResolver` when there are any, and
    /// spring's defaults otherwise (`HttpSessionSecurityContextRepository`,
    /// `AuthenticationTrustResolverImpl`). It reads and sets the context on the same
    /// `SecurityContextHolderStrategy` spring security's own filters use: the chain's shared one if
    /// any, else the application context's `SecurityContextHolderStrategy` bean if there is exactly
    /// one, else `SecurityContextHolder`'s.
    ///
    /// @param <R> the application principal type
    public static class PrincipalsConfigurer<R> extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

        private final List<PrincipalMappingStrategy<?, R>> mappers = new ArrayList<>();
        private final Class<R> principalType;

        /// @param principalType the application principal type
        public PrincipalsConfigurer(Class<R> principalType) {
            this.principalType = principalType;
        }

        /// Adds a mapping with its own notion of which principals it supports.
        ///
        /// @param mapper the strategy to add
        /// @return this configurer
        public PrincipalsConfigurer<R> principal(PrincipalMappingStrategy<Object, R> mapper) {
            this.mappers.add(mapper);
            return this;
        }

        /// Maps the principals that are instances of a type, subtypes included.
        ///
        /// @param <T> the principal type handled
        /// @param old the principal type handled
        /// @param mapper maps a principal of that type
        /// @return this configurer
        public <T> PrincipalsConfigurer<R> principal(Class<T> old, PrincipalMapper<T, R> mapper) {
            this.mappers.add(new PrincipalMappingStrategy.ByType<>(old, mapper));
            return this;
        }

        /// Replaces the principals equal to a value with a fixed one, e.g. spring's anonymous
        /// `anonymousUser` with a guest principal.
        ///
        /// @param old the principal handled, compared with `equals`
        /// @param replacement the application principal used in its place
        /// @return this configurer
        public PrincipalsConfigurer<R> principal(Object old, R replacement) {
            this.mappers.add(new PrincipalMappingStrategy.ByInstance<>(old, (Authentication auth, Object principal) -> {
                return replacement;
            }));
            return this;
        }

        /// Adds the filter to the chain, see the type's documentation for where and with what.
        ///
        /// @param http the security being built
        @Override
        public void configure(HttpSecurity http) {
            final var scr = http.getSharedObject(SecurityContextRepository.class);
            final var mscr = scr != null ? scr : new HttpSessionSecurityContextRepository();

            final var mschs = securityContextHolderStrategy(http);

            final var atr = http.getSharedObject(AuthenticationTrustResolver.class);
            final var matr = atr != null ? atr : new AuthenticationTrustResolverImpl();

            final var filter = new AuthenticationsCoalescingFilter<>(mschs, mscr, matr, mappers, principalType);

            postProcess(filter);
            http.addFilterBefore(filter, SessionManagementFilter.class);
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

}
