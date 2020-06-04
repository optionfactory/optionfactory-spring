package net.optionfactory.spring.authentication.bearer.token;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication through {@code Authorization: Bearer token} header.
 */
public class BearerTokenAuthentication extends AbstractAuthenticationToken {

    private final String token;

    public BearerTokenAuthentication(String token) {
        this(token, null);
    }

    public BearerTokenAuthentication(String token, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.token = token;
        super.setAuthenticated(authorities != null);
    }

    public BearerTokenAuthentication withAuthorities(Collection<? extends GrantedAuthority> authorities) {
        final BearerTokenAuthentication bearer = new BearerTokenAuthentication(token, authorities);
        bearer.setDetails(getDetails());
        return bearer;
    }

    @Override
    public String getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return null;
    }
}
