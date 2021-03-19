package net.optionfactory.spring.authentication.bearer.token;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication through {@code Authorization: Bearer token} header.
 */
public class BearerTokenAuthentication extends AbstractAuthenticationToken {

    private final String token;
    private final Object principal;
    
    
    public BearerTokenAuthentication(String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.token = token;
        this.principal = principal;
        super.setAuthenticated(authorities != null);
    }

    public BearerTokenAuthentication makeAuthenticated(Object principal, Collection<? extends GrantedAuthority> authorities) {
        final BearerTokenAuthentication bearer = new BearerTokenAuthentication(token, principal, authorities);
        bearer.setDetails(getDetails());
        return bearer;
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
