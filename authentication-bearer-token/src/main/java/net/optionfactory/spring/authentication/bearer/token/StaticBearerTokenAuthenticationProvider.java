package net.optionfactory.spring.authentication.bearer.token;

import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authenticates by matching the configured static token with the request Bearer
 * token, granting the configured authorities on authentication success.
 */
public class StaticBearerTokenAuthenticationProvider implements AuthenticationProvider {

    private final Map<String, PrincipalAndAuthorities> tokenToPrincipalAndAuthorities;

    public StaticBearerTokenAuthenticationProvider(Map<String, PrincipalAndAuthorities> tokenToPrincipalAndAuthorities) {
        this.tokenToPrincipalAndAuthorities = tokenToPrincipalAndAuthorities;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final BearerTokenAuthentication bearer = (BearerTokenAuthentication) authentication;
        final PrincipalAndAuthorities got = tokenToPrincipalAndAuthorities.get(bearer.getCredentials());
        return got == null ? null : bearer.makeAuthenticated(got.principal, got.authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthentication.class.isAssignableFrom(authentication);
    }

    public static StaticBearerTokenAuthenticationProvider of(String token, Object principal, List<GrantedAuthority> authorities) {
        return new StaticBearerTokenAuthenticationProvider(Map.of(token, new PrincipalAndAuthorities(principal, authorities)));
    }

    public static class PrincipalAndAuthorities {

        public final Object principal;
        public final List<GrantedAuthority> authorities;

        public PrincipalAndAuthorities(Object principal, List<GrantedAuthority> authorities) {
            this.principal = principal;
            this.authorities = authorities;
        }

    }
}
