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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

/// Looks for a token on the configured headers and schemes and authenticates it with the
/// `AuthenticationManager`.
///
/// A header matches when its value starts with the scheme, compared case-insensitively and
/// independently of the default locale; the token is the rest of the value, trimmed. A blank scheme
/// takes the header's whole value as the token. Only the first value of a repeated header is read.
///
/// The outcome never ends the request here, the request always proceeds down the chain:
///
/// - an accepted token replaces the request's context, on the configured
///   `SecurityContextHolderStrategy` (see
///   [#setSecurityContextHolderStrategy(SecurityContextHolderStrategy)]), with a new one holding
///   the resulting authentication, for this request only: nothing is saved to a
///   `SecurityContextRepository`, so the token has to be presented on every request. The context
///   is replaced rather than changed in place because the one loaded from an `HttpSession` is the
///   very instance stored in the session: changing it would make the token's identity stick to the
///   session, and to every later request on it, token or not;
/// - a rejected token contributes no authentication but invalidates nothing: authentication
///   mechanisms earlier in the chain keep whatever they established. The failure handler is
///   deliberately not triggered, as other authentication filters (notably
///   `BearerTokenAuthenticationFilter`) might want to process an `Authorization` header token. The
///   rejection is logged at DEBUG, by exception type only;
/// - a request carrying more than one of the configured tokens is ambiguous: none of them is
///   authenticated, and it is announced at WARN, naming the headers and schemes the tokens were
///   found in, never the tokens.
public class HttpHeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(HttpHeaderAuthenticationFilter.class);

    private final AuthenticationManager am;
    private final List<HeaderAndScheme> hss;
    private SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    /// @param am authenticates the [UnauthenticatedToken] found
    /// @param hss the headers and schemes to search
    public HttpHeaderAuthenticationFilter(AuthenticationManager am, LinkedHashSet<HeaderAndScheme> hss) {
        this.am = am;
        this.hss = hss.stream().toList();
    }

    /// Sets the strategy the token's authentication is set on, as spring security's own filters
    /// do. [HttpHeaderAuthentication.Configurer] sets the application's strategy, see there.
    ///
    /// @param securityContextHolderStrategy the strategy to use, defaulting to
    /// `SecurityContextHolder`'s
    public void setSecurityContextHolderStrategy(SecurityContextHolderStrategy securityContextHolderStrategy) {
        Assert.notNull(securityContextHolderStrategy, "securityContextHolderStrategy cannot be null");
        this.securityContextHolderStrategy = securityContextHolderStrategy;
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
                final SecurityContext context = securityContextHolderStrategy.createEmptyContext();
                context.setAuthentication(authentication);
                securityContextHolderStrategy.setContext(context);
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
