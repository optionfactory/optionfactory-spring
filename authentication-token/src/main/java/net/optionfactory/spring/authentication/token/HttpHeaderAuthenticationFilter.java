package net.optionfactory.spring.authentication.token;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import net.optionfactory.spring.authentication.token.HttpHeaderAuthentication.UnauthenticatedToken;
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
        this.authScheme = authScheme;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final Optional<UnauthenticatedToken> bearer = searchTokenByPrefix(request, headerName, authScheme);
        if (bearer.isPresent()) {
            try {
                final Authentication authentication = am.authenticate(bearer.get());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AuthenticationException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    public static Optional<UnauthenticatedToken> searchTokenByPrefix(HttpServletRequest request, String headerName, String authScheme) {
        return Optional.ofNullable(request.getHeader(headerName))
                .filter(header -> header.toUpperCase().startsWith(authScheme))
                .map(header -> header.substring(authScheme.length()).trim())
                .map(token -> new UnauthenticatedToken(token, request));
    }
}
