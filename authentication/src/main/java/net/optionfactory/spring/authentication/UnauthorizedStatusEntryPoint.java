package net.optionfactory.spring.authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/// Answers an unauthenticated request with a bare `401 Unauthorized`, optionally announcing the
/// expected authentication scheme in `WWW-Authenticate`.
///
/// Meant for apis authenticated with tokens, where spring's defaults (a redirect to a login page,
/// or a basic challenge that makes browsers prompt for credentials) are not wanted. No body is
/// written.
///
/// ```java
/// http.exceptionHandling(eh -> eh.authenticationEntryPoint(UnauthorizedStatusEntryPoint.bearerChallenge()));
/// ```
public class UnauthorizedStatusEntryPoint implements AuthenticationEntryPoint {

    private final String challenge;

    /// @param challenge the `WWW-Authenticate` value, written verbatim, or `null` for none
    public UnauthorizedStatusEntryPoint(@Nullable String challenge) {
        this.challenge = challenge;
    }

    /// Sets the status to 401 and, when configured, the challenge header.
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        if (challenge != null) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
        }
    }

    /// @param as the `WWW-Authenticate` value, written verbatim, e.g. `Basic realm="api"`
    /// @return an entry point announcing that challenge
    public static UnauthorizedStatusEntryPoint authScheme(String as) {
        return new UnauthorizedStatusEntryPoint(as);
    }

    /// @return an entry point announcing `WWW-Authenticate: Bearer`
    public static UnauthorizedStatusEntryPoint bearerChallenge() {
        return new UnauthorizedStatusEntryPoint("Bearer");
    }

    /// @return an entry point setting the status only, with no `WWW-Authenticate` header
    public static UnauthorizedStatusEntryPoint noChallenge() {
        return new UnauthorizedStatusEntryPoint(null);
    }

}
