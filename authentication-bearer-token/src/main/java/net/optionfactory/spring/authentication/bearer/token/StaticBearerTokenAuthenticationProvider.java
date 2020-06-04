package net.optionfactory.spring.authentication.bearer.token;

import java.util.Collection;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authenticates by matching the configured static token with the request Bearer
 * token, granting the configured authorities on authentication success.
 */
public class StaticBearerTokenAuthenticationProvider implements AuthenticationProvider {

    private final String token;
    private final Collection<? extends GrantedAuthority> authorities;

    public StaticBearerTokenAuthenticationProvider(String token, Collection<? extends GrantedAuthority> authorities) {
        this.token = token;
        this.authorities = authorities;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final BearerTokenAuthentication bearer = (BearerTokenAuthentication) authentication;
        if (!token.equals(bearer.getCredentials())) {
            throw new BadCredentialsException("Invalid token");
        }
        return bearer.withAuthorities(authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthentication.class.isAssignableFrom(authentication);
    }
}
