package net.optionfactory.spring.authentication.bearer.token;

import org.springframework.security.authentication.AbstractAuthenticationToken;

public class BearerToken extends AbstractAuthenticationToken {

    private final String token;
    
    public BearerToken(String token) {
        super(null);
        this.token = token;
        super.setAuthenticated(false);
    }

    @Override
    public String getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }
}
