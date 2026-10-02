package net.optionfactory.spring.authentication;

import org.springframework.security.core.Authentication;

/// A [PrincipalMapper] that also says which principals it handles.
///
/// [AuthenticationsCoalescingFilter] consults its strategies in order and the first one that
/// supports a principal maps it: later strategies are not consulted, even when the chosen one maps
/// the principal to `null`.
///
/// @param <T> the principal type this strategy reads
/// @param <R> the application principal type
public interface PrincipalMappingStrategy<T, R> extends PrincipalMapper<T, R> {

    /// @param auth the authentication carrying the principal
    /// @param principal the principal to map, never `null`
    /// @return true when this strategy maps the principal
    boolean supports(Authentication auth, Object principal);

    /// Maps the principals that are instances of a type, subtypes included: e.g. `UserDetails` for
    /// form login, `OidcUser` for an authorization code login, `Jwt` for a resource server.
    ///
    /// @param <T> the principal type
    /// @param <R> the application principal type
    public static class ByType<T, R> implements PrincipalMappingStrategy<T, R> {

        private final Class<T> type;
        private final PrincipalMapper<T, R> mapper;

        /// @param type the principal type handled
        /// @param mapper maps a principal of that type
        public ByType(Class<T> type, PrincipalMapper<T, R> mapper) {
            this.type = type;
            this.mapper = mapper;
        }

        /// @return true when the principal is an instance of the configured type
        @Override
        public boolean supports(Authentication auth, Object principal) {
            return type.isInstance(principal);
        }

        /// @return what the configured mapper makes of the principal, cast to the configured type
        @Override
        public R map(Authentication auth, Object principal) {
            return mapper.map(auth, type.cast(principal));
        }

    }

    /// Maps the principals equal to a given value: typically the string principals some mechanisms
    /// carry, such as `anonymousUser` for spring's anonymous authentication, or the principal
    /// configured for a static token.
    ///
    /// @param <R> the application principal type
    public static class ByInstance<R> implements PrincipalMappingStrategy<Object, R> {

        private final Object old;
        private final PrincipalMapper<Object, R> mapper;

        /// @param old the principal handled, compared with `equals`
        /// @param mapper maps that principal
        public ByInstance(Object old, PrincipalMapper<Object, R> mapper) {
            this.old = old;
            this.mapper = mapper;
        }

        /// @return true when the principal equals the configured one
        @Override
        public boolean supports(Authentication auth, Object principal) {
            return old.equals(principal);
        }

        /// @return what the configured mapper makes of the principal
        @Override
        public R map(Authentication auth, Object principal) {
            return mapper.map(auth, principal);
        }

    }
}
