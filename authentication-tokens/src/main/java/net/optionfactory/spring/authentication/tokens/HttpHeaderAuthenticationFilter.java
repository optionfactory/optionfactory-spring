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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>
 * A rejected or ambiguous token contributes no authentication, but invalidates
 * nothing: authentication mechanisms earlier in the chain keep whatever they
 * established, and the request proceeds. A request carrying more than one of
 * the configured tokens is ambiguous and is announced at WARN, naming the
 * headers and schemes it was found in, never the tokens.
 */
public class HttpHeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(HttpHeaderAuthenticationFilter.class);

    private final AuthenticationManager am;
    private final List<HeaderAndScheme> hss;

    public HttpHeaderAuthenticationFilter(AuthenticationManager am, LinkedHashSet<HeaderAndScheme> hss) {
        this.am = am;
        this.hss = hss.stream().toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final var tokens = searchTokens(request);
        if (tokens.size() > 1) {
            final var sources = tokens.stream().map(t -> t.getHeaderAndScheme().header() + ": " + t.getHeaderAndScheme().scheme().strip()).toList();
            logger.warn("ambiguous authentication: request carries {} tokens ({}); none will be authenticated", tokens.size(), sources);
        } else if (tokens.size() == 1) {
            try {
                final Authentication authentication = am.authenticate(tokens.get(0));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AuthenticationException exception) {
                logger.debug("token authentication rejected: {}", exception.getClass().getSimpleName());
            }
        }
        filterChain.doFilter(request, response);
    }

    private List<UnauthenticatedToken> searchTokens(HttpServletRequest request) {
        return this.hss.stream()
                .map(ts ->
                        Optional.ofNullable(request.getHeader(ts.header()))
                            .filter(v -> v.regionMatches(true, 0, ts.scheme(), 0, ts.scheme().length()))
                            .map(v -> v.substring(ts.scheme().length()).trim())
                            .map(token -> new UnauthenticatedToken(ts, token, request))
                ).filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
