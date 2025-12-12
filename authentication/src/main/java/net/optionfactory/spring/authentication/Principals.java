package net.optionfactory.spring.authentication;

import java.util.ArrayList;
import java.util.List;
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

public class Principals {

    public static <T> PrincipalsConfigurer<T> coalescing(Class<T> principalType) {
        return new PrincipalsConfigurer<>(principalType);
    }

    public static class PrincipalsConfigurer<R> extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

        private final List<PrincipalMappingStrategy<?, R>> mappers = new ArrayList<>();
        private final Class<R> principalType;

        public PrincipalsConfigurer(Class<R> principalType) {
            this.principalType = principalType;
        }

        public PrincipalsConfigurer principal(PrincipalMappingStrategy<Object, R> mapper) {
            this.mappers.add(mapper);
            return this;
        }

        public <T> PrincipalsConfigurer principal(Class<T> old, PrincipalMapper<T, R> mapper) {
            this.mappers.add(new PrincipalMappingStrategy.ByType<>(old, mapper));
            return this;
        }

        public PrincipalsConfigurer principal(Object old, R replacement) {
            this.mappers.add(new PrincipalMappingStrategy.ByInstance<>(old, (Authentication auth, Object principal) -> {
                return replacement;
            }));
            return this;
        }

        @Override
        public void configure(HttpSecurity http) {
            //see FilterOrderRegistration

            final var scr = http.getSharedObject(SecurityContextRepository.class);
            final var mscr = scr != null ? scr : new HttpSessionSecurityContextRepository();

            final var schs = http.getSharedObject(SecurityContextHolderStrategy.class);
            final var mschs = schs != null ? schs : SecurityContextHolder.getContextHolderStrategy();

            final var atr = http.getSharedObject(AuthenticationTrustResolver.class);
            final var matr = atr != null ? atr : new AuthenticationTrustResolverImpl();

            final var filter = new AuthenticationsCoalescingFilter(mschs, mscr, matr, mappers, principalType);

            postProcess(filter);
            http.addFilterBefore(filter, SessionManagementFilter.class);
        }
    }

}
