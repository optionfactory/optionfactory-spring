package net.optionfactory.spring.authentication.tokens;

import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.AuthenticatedToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

public class HttpHeaderAuthenticationFilterTest {

    @Test
    public void schemeMatchIsLocaleIndependent() throws Exception {
        final Locale original = Locale.getDefault();
        final var recorded = new AtomicBoolean(false);
        final AuthenticationManager am = (Authentication authentication) -> {
            recorded.set(true);
            return new AuthenticatedToken(
                    authentication.getCredentials().toString(),
                    "principal",
                    authentication.getDetails(),
                    AuthorityUtils.NO_AUTHORITIES
            );
        };
        final var filter = new HttpHeaderAuthenticationFilter(
                am,
                new java.util.LinkedHashSet<>(List.of(new HeaderAndScheme("Authorization", "BASIC ")))
        );
        final MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "basic dXNlcjpwYXNz");
        final MockHttpServletResponse res = new MockHttpServletResponse();
        final FilterChain chain = (request, response) -> {
        };

        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            filter.doFilter(req, res, chain);
        } finally {
            Locale.setDefault(original);
        }

        Assertions.assertTrue(recorded.get(), "lowercase 'basic' scheme must still match under Turkish locale");
    }
}
