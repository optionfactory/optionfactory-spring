package net.optionfactory.spring.authentication.bearer.token;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configures the {@link BearerTokenAuthenticationFilter} by injecting the
 * Spring managed {@link AuthenticationManager} and registers it with the HTTP
 * security.
 */
public class BearerTokenAuthenticationFilterConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

    @Override
    public void configure(HttpSecurity builder) {
        final AuthenticationManager authenticationManager = builder.getSharedObject(AuthenticationManager.class);
        final BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(authenticationManager);
        postProcess(filter);
        builder.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
    }
}
