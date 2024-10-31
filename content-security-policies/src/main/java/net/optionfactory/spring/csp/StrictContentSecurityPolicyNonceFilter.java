package net.optionfactory.spring.csp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * should be registered before HeaderWriterFilter.class
 *
 */
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

    /**
     * We are using a record instead of just the value when setting the nonce as
     * a request attribute/model attribute to always prevent the value to be
     * exposed as a query parameter during redirects. This behaviour is defined
     * by {@code RedirectView}s configured with {@code exposeModelAttributes}
     * and {@code RequestMappingHandlerAdapter} configured without
     * {@code ignoreDefaultModelOnRedirect}. Wrapping the value in a record
     * makes it ineligible for forwarding as per the implementation in
     * {@code RedirectView.isEligibleValue}
     */
    public record Csp(String nonce) {

    }

}
