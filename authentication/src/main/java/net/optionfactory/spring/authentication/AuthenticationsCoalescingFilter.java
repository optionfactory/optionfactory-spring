package net.optionfactory.spring.authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
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

        final Authentication newAuth = authenticationTrustResolver.isAnonymous(auth)
                ? new AnonymousAuthenticationToken("anon-auth-key", mappedPrincipal, auth.getAuthorities())
                : new CoalescingAuthentication(auth, mappedPrincipal);

        final var sctx = securityContextHolderStrategy.createEmptyContext();
        sctx.setAuthentication(newAuth);

        this.securityContextHolderStrategy.setContext(sctx);
        this.securityContextRepository.saveContext(sctx, request, response);

        filterChain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
    private static <R> R mapPrincipal(List<PrincipalMappingStrategy<?, R>> mappers, Authentication auth, Object principal) {
        for (final var mapper : mappers) {
            if (mapper.supports(auth, principal)) {
                final var tmapper = (PrincipalMappingStrategy<Object, R>) mapper;
                return tmapper.map(auth, principal);
            }
        }
        throw new IllegalStateException(String.format("unmappable principal '%s'", principal));
    }

}
