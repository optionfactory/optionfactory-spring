package net.optionfactory.spring.authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/// Replaces the principal of the request's authentication with the application's own principal
/// type, as mapped by the first [PrincipalMappingStrategy] supporting it. Usually installed through
/// [Principals#coalescing(Class)] rather than directly.
///
/// For each request:
///
/// - no authentication, a `null` principal, or a principal already of the application type: the
///   request proceeds untouched;
/// - an authenticated principal: it is replaced by a [CoalescingAuthentication] carrying the mapped
///   principal and delegating everything else to the original authentication;
/// - an anonymous principal (as told by the `AuthenticationTrustResolver`): it is replaced by an
///   `AnonymousAuthenticationToken` with the mapped principal and the original authorities, so that
///   it is still recognised as anonymous; with no strategy supporting it, the request proceeds
///   untouched, since an application should not have to invent a principal for callers that have
///   not authenticated.
///
/// An authenticated principal that no strategy supports, or that its strategy maps to `null`, fails
/// the request with an `IllegalStateException`: it is a misconfiguration, and letting the request
/// through with a principal of an unexpected type would only move the failure into the
/// application.
///
/// The replaced context is set on the holder strategy and saved to the `SecurityContextRepository`,
/// so with a session-backed repository later requests load the already-coalesced authentication and
/// pass straight through.
///
/// @param <R> the application principal type
public class AuthenticationsCoalescingFilter<R> extends OncePerRequestFilter {

    private final SecurityContextHolderStrategy securityContextHolderStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AuthenticationTrustResolver authenticationTrustResolver;

    private final List<PrincipalMappingStrategy<?, R>> mappers;
    private final Class<R> principalType;

    /// @param securityContextHolderStrategy where the request's authentication is read and replaced
    /// @param securityContextRepository where the replaced context is saved
    /// @param authenticationTrustResolver tells anonymous authentications apart
    /// @param mappers the strategies, consulted in order
    /// @param principalType the application principal type
    public AuthenticationsCoalescingFilter(
            SecurityContextHolderStrategy securityContextHolderStrategy,
            SecurityContextRepository securityContextRepository,
            AuthenticationTrustResolver authenticationTrustResolver,
            List<PrincipalMappingStrategy<?, R>> mappers,
            Class<R> principalType) {
        this.securityContextHolderStrategy = securityContextHolderStrategy;
        this.securityContextRepository = securityContextRepository;
        this.authenticationTrustResolver = authenticationTrustResolver;
        this.mappers = mappers;
        this.principalType = principalType;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final var auth = securityContextHolderStrategy.getContext().getAuthentication();

        if (auth == null || auth.getPrincipal() == null || principalType.isInstance(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        final var mappedPrincipal = mapPrincipal(mappers, auth, auth.getPrincipal());
        final var anonymous = authenticationTrustResolver.isAnonymous(auth);

        if (mappedPrincipal.isEmpty()) {
            if (!anonymous) {
                throw new IllegalStateException(String.format("unmappable principal '%s'", auth.getPrincipal()));
            }
            filterChain.doFilter(request, response);
            return;
        }

        final Authentication newAuth = anonymous
                ? new AnonymousAuthenticationToken("anon-auth-key", mappedPrincipal.get(), auth.getAuthorities())
                : new CoalescingAuthentication(auth, mappedPrincipal.get());

        final var sctx = securityContextHolderStrategy.createEmptyContext();
        sctx.setAuthentication(newAuth);

        this.securityContextHolderStrategy.setContext(sctx);
        this.securityContextRepository.saveContext(sctx, request, response);

        filterChain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
    private static <R> Optional<R> mapPrincipal(List<PrincipalMappingStrategy<?, R>> mappers, Authentication auth, Object principal) {
        for (final var mapper : mappers) {
            if (mapper.supports(auth, principal)) {
                final var tmapper = (PrincipalMappingStrategy<Object, R>) mapper;
                return Optional.ofNullable(tmapper.map(auth, principal));
            }
        }
        return Optional.empty();
    }

}
