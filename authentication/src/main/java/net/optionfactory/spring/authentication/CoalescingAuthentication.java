package net.optionfactory.spring.authentication;

import java.util.Collection;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public class CoalescingAuthentication implements Authentication {

    private final Authentication source;
    private final Object principal;

    public CoalescingAuthentication(Authentication source, Object principal) {
        this.source = source;
        this.principal = principal;
    }

    public Authentication source() {
        return source;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return source.getAuthorities();
    }

    @Override
    public Object getCredentials() {
        return source.getCredentials();
    }

    @Override
    public Object getDetails() {
        return source.getDetails();
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public boolean isAuthenticated() {
        return source.isAuthenticated();
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        source.setAuthenticated(isAuthenticated);
    }

    @Override
    public String getName() {
        return source.getName();
    }

}
