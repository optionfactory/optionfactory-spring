package net.optionfactory.spring.authentication.token;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Suggests the client to use the configured authScheme token authentication
 * mechanism.
 */
public class UnauthorizedStatusEntryPoint implements AuthenticationEntryPoint {

    private final String challenge;

    public UnauthorizedStatusEntryPoint(@Nullable String challenge) {
        this.challenge = challenge;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        if (challenge != null) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
        }
    }

    public static UnauthorizedStatusEntryPoint bearerChallenge() {
        return new UnauthorizedStatusEntryPoint("Bearer");
    }

    public static UnauthorizedStatusEntryPoint noChallenge() {
        return new UnauthorizedStatusEntryPoint(null);
    }

}
