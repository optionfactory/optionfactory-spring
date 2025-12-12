package net.optionfactory.spring.authentication;

import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public class CoalescingAuthentication implements Authentication, Serializable {

    private static final long serialVersionUID = 1L;

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

    @Override
    public String toString() {
        return String.format("CoalescingAuthentication [Principal=%s, Original=%s, Authorities=%s]", this.principal, this.source.getClass().getSimpleName(), this.getAuthorities());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CoalescingAuthentication that)) {
            return false;
        }
        return Objects.equals(this.principal, that.principal);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(principal);
    }

}
