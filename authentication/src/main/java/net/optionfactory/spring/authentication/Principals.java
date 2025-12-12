package net.optionfactory.spring.authentication;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.session.SessionManagementFilter;

public class Principals {

    public static <T> PrincipalsConfigurer<T> coalescing(Class<T> principalType) {
        return new PrincipalsConfigurer<>();
    }

    public static class PrincipalsConfigurer<T> extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

        private final List<PrincipalMapper<T>> mappers = new ArrayList<>();

        public PrincipalsConfigurer principal(PrincipalMapper<T> mapper) {
            this.mappers.add(mapper);
            return this;
        }

        public PrincipalsConfigurer principal(Object old, T replacement) {
            this.mappers.add(new PrincipalMapper<T>() {
                @Override
                public boolean supports(Authentication auth, Object principal) {
                    return old.equals(principal);
                }

                @Override
                public T map(Authentication auth, Object principal) {
                    return replacement;
                }
            });
            return this;
        }

        @Override
        public void configure(HttpSecurity http) {
            //see FilterOrderRegistration
            final var filter = new AuthenticationsCoalescingFilter(mappers);
            postProcess(filter);
            http.addFilterBefore(filter, SessionManagementFilter.class);
        }
    }

}
