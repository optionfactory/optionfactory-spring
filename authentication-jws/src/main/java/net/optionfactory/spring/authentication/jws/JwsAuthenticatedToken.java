package net.optionfactory.spring.authentication.jws;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class JwsAuthenticatedToken extends AbstractAuthenticationToken {

    private final String token;
    private final Object principal;
    
    
    public JwsAuthenticatedToken(String token, Object principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.token = token;
        this.principal = principal;
        super.setAuthenticated(authorities != null);
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
