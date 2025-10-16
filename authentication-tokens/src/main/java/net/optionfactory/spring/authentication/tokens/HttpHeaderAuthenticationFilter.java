package net.optionfactory.spring.authentication.tokens;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.UnauthenticatedToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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
    private final List<HeaderAndScheme> hss;

    public HttpHeaderAuthenticationFilter(AuthenticationManager am, LinkedHashSet<HeaderAndScheme> hss) {
        this.am = am;
        this.hss = hss.stream().toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        searchToken(request).ifPresent(token -> {
            try {
                final Authentication authentication = am.authenticate(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AuthenticationException exception) {
                SecurityContextHolder.clearContext();
            }
        });
        filterChain.doFilter(request, response);
    }

    private Optional<UnauthenticatedToken> searchToken(HttpServletRequest request) {
        var tokens = this.hss.stream()
                .map(ts ->
                        Optional.ofNullable(request.getHeader(ts.header()))
                            .filter(v -> v.toUpperCase().startsWith(ts.scheme()))
                            .map(v -> v.substring(ts.scheme().length()).trim())
                            .map(token -> new UnauthenticatedToken(ts, token, request))
                ).filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        if (tokens.size() > 1) {
            throw new BadCredentialsException("Multiple tokens found");
        }
        return tokens.stream().findFirst();
    }
}
