package net.optionfactory.spring.authentication.tokens;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.UnauthenticatedToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authentication mechanism using a request's HTTP header, looking for an
 * auth-scheme token. This filter deliberately avoids triggering the failure
 * handler on AuthenticationException as other authentication filters (notably
 * BearerTokenAuthenticationFilter) might want to process an Authorization
 * header token .
 */
public class HttpHeaderAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationManager am;
    private final String headerName;
    private final String authScheme;

    public HttpHeaderAuthenticationFilter(AuthenticationManager am, String headerName, String authScheme) {
        this.am = am;
        this.headerName = headerName;
        this.authScheme = authScheme.toUpperCase().trim() + " ";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        searchToken(request, headerName, authScheme).ifPresent(token -> {
            try {
                final Authentication authentication = am.authenticate(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AuthenticationException exception) {
                SecurityContextHolder.clearContext();
            }
        });
        filterChain.doFilter(request, response);
    }

    public static Optional<UnauthenticatedToken> searchToken(HttpServletRequest request, String headerName, String authScheme) {
        return Optional.ofNullable(request.getHeader(headerName))
                .filter(header -> header.toUpperCase().startsWith(authScheme))
                .map(header -> header.substring(authScheme.length()).trim())
                .map(token -> new UnauthenticatedToken(token, request));
    }
}
