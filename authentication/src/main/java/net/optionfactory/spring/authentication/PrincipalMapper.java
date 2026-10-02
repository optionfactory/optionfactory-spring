package net.optionfactory.spring.authentication;

import org.springframework.security.core.Authentication;

/// Turns the principal an authentication mechanism produced into the application's own principal
/// type.
///
/// @param <T> the principal type this mapper reads
/// @param <R> the application principal type
public interface PrincipalMapper<T, R> {

    /// @param auth the authentication carrying the principal, for mappers that also need its
    /// authorities, details or credentials
    /// @param principal the principal to map
    /// @return the application principal; `null` means the principal cannot be mapped, which
    /// [AuthenticationsCoalescingFilter] treats as a misconfiguration for an authenticated request
    R map(Authentication auth, T principal);
}
