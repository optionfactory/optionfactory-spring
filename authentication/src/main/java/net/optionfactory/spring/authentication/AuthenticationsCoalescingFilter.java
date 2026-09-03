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

public class AuthenticationsCoalescingFilter<R> extends OncePerRequestFilter {

    private final SecurityContextHolderStrategy securityContextHolderStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AuthenticationTrustResolver authenticationTrustResolver;

    private final List<PrincipalMappingStrategy<?, R>> mappers;
    private final Class<R> principalType;

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
            // an anonymous request carries no identity to normalise, so it is left as spring made
            // it: an application should not have to invent a principal for callers that have not
            // authenticated
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
