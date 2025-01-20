package net.optionfactory.spring.authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthenticationsCoalescingFilter<T> extends OncePerRequestFilter {

    private SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    private SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    private AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

    private final List<PrincipalMapper<T>> mappers;

    public AuthenticationsCoalescingFilter(List<PrincipalMapper<T>> mappers) {
        this.mappers = mappers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final var sctx = securityContextHolderStrategy.getContext();
        final var auth = sctx.getAuthentication();
        if (auth != null) {
            final var principal = mapPrincipal(mappers, auth, auth.getPrincipal());
            final Authentication newAuth = authenticationTrustResolver.isAnonymous(auth)
                    ? new AnonymousAuthenticationToken("anon-auth-key", principal, auth.getAuthorities())
                    : new CoalescingAuthentication(auth, principal);
            sctx.setAuthentication(newAuth);
            this.securityContextHolderStrategy.setContext(sctx);
            this.securityContextRepository.saveContext(sctx, request, response);
        }
        filterChain.doFilter(request, response);
    }

    private static <T> T mapPrincipal(List<PrincipalMapper<T>> mappers, Authentication auth, Object principal) {
        for (final var mapper : mappers) {
            if (mapper.supports(auth, principal)) {
                return mapper.map(auth, principal);
            }
        }
        throw new IllegalStateException(String.format("unmappable principal '%s'", principal));
    }

    public void setSecurityContextHolderStrategy(SecurityContextHolderStrategy securityContextHolderStrategy) {
        this.securityContextHolderStrategy = securityContextHolderStrategy;
    }

    public void setSecurityContextRepository(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public void setAuthenticationTrustResolver(AuthenticationTrustResolver authenticationTrustResolver) {
        this.authenticationTrustResolver = authenticationTrustResolver;
    }

}
