package net.optionfactory.spring.authentication;

import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/// An authentication whose principal has been replaced by the application's own, as set by
/// [AuthenticationsCoalescingFilter].
///
/// Only the principal changes: authorities, credentials, details, the authenticated flag and the
/// name all come from the original authentication, which stays available through [#source()]. So
/// `getName()` is still the name the authentication mechanism assigned, not something derived from
/// the mapped principal.
///
/// Two coalescing authentications are equal when their principals are, whatever their sources.
/// Being `Serializable`, it can be stored in an http session as long as the source and the
/// principal are serializable too.
public class CoalescingAuthentication implements Authentication, Serializable {

    private static final long serialVersionUID = 1L;

    private final Authentication source;
    private final Object principal;

    /// @param source the authentication produced by the authentication mechanism
    /// @param principal the application principal replacing the source's
    public CoalescingAuthentication(Authentication source, Object principal) {
        this.source = source;
        this.principal = principal;
    }

    /// @return the authentication produced by the authentication mechanism, e.g. to reach an
    /// `OAuth2AuthenticationToken`'s registration id
    public Authentication source() {
        return source;
    }

    /// @return the source's authorities
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return source.getAuthorities();
    }

    /// @return the source's credentials
    @Override
    public Object getCredentials() {
        return source.getCredentials();
    }

    /// @return the source's details
    @Override
    public Object getDetails() {
        return source.getDetails();
    }

    /// @return the application principal
    @Override
    public Object getPrincipal() {
        return principal;
    }

    /// @return the source's authenticated flag
    @Override
    public boolean isAuthenticated() {
        return source.isAuthenticated();
    }

    /// Sets the flag on the source, which this authentication reads it from.
    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        source.setAuthenticated(isAuthenticated);
    }

    /// @return the source's name, as assigned by the authentication mechanism
    @Override
    public String getName() {
        return source.getName();
    }

    /// @return the principal, the source's type and the authorities; never the credentials
    @Override
    public String toString() {
        return String.format("CoalescingAuthentication [Principal=%s, Original=%s, Authorities=%s]", this.principal, this.source.getClass().getSimpleName(), this.getAuthorities());
    }

    /// @return true when `o` is a coalescing authentication with an equal principal
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

    /// @return the principal's hash code
    @Override
    public int hashCode() {
        return Objects.hashCode(principal);
    }

}
