package net.optionfactory.spring.csp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.web.filter.OncePerRequestFilter;

/// Generates the nonce of each request: 16 bytes from a `SecureRandom`, hex-encoded, exposed as a
/// [Csp] in the `csp` request attribute.
///
/// It must run before `HeaderWriterFilter`, which writes the policy carrying the nonce. Error
/// dispatches are filtered too, so that an error page rendered by the container still finds a
/// nonce.
public class StrictContentSecurityPolicyNonceFilter extends OncePerRequestFilter {

    private final SecureRandom sr = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final byte[] nonce = new byte[16];
        sr.nextBytes(nonce);
        final String hexNonce = new String(Hex.encode(nonce));
        request.setAttribute("csp", new Csp(hexNonce));
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    /// The request's nonce, as exposed to the request and to templates (`${csp.nonce}`).
    ///
    /// A record is used instead of the bare value so that the nonce is never exposed as a query
    /// parameter on redirects: a `RedirectView` configured with `exposeModelAttributes` (and a
    /// `RequestMappingHandlerAdapter` configured without `ignoreDefaultModelOnRedirect`) appends the
    /// model's simple values to the redirect url, and `RedirectView.isEligibleValue` does not
    /// consider a record one.
    ///
    /// @param nonce the hex-encoded nonce
    public record Csp(String nonce) {

    }

}
