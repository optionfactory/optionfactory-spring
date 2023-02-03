package net.optionfactory.spring.csp;

import java.io.IOException;
import java.security.SecureRandom;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
        request.setAttribute("cspnonce", hexNonce);
        filterChain.doFilter(request, response);
    }

}
