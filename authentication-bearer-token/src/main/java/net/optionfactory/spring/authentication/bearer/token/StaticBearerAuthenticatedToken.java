package net.optionfactory.spring.authentication.bearer.token;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication through {@code Authorization: Bearer token} header.
 */
public class StaticBearerAuthenticatedToken extends AbstractAuthenticationToken {

    private final String token;
    private final Object principal;
    
    
    public StaticBearerAuthenticatedToken(String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.token = token;
        this.principal = principal;
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
