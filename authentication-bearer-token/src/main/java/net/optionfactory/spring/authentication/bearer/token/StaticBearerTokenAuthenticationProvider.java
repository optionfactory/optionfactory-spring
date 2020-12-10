package net.optionfactory.spring.authentication.bearer.token;

import java.util.Collection;
import java.util.Map;
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

    private final Map<String, ? extends Collection<? extends GrantedAuthority>> tokenToAuthorities;

    public StaticBearerTokenAuthenticationProvider(String token, Collection<? extends GrantedAuthority> authorities) {
        this(Map.of(token, authorities));
    }

    public StaticBearerTokenAuthenticationProvider(Map<String, ? extends Collection<? extends GrantedAuthority>> tokenToAuthorities) {
        this.tokenToAuthorities = tokenToAuthorities;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final BearerTokenAuthentication bearer = (BearerTokenAuthentication) authentication;
        if (!tokenToAuthorities.containsKey(bearer.getCredentials())) {
            throw new BadCredentialsException("Invalid token");
        }
        return bearer.withAuthorities(tokenToAuthorities.get(bearer.getCredentials()));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthentication.class.isAssignableFrom(authentication);
    }
}
