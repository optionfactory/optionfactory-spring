package net.optionfactory.spring.authentication.tokens;

import jakarta.servlet.FilterChain;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
                new LinkedHashSet<>(List.of(new HeaderAndScheme("Authorization", "BASIC ")))
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
    @Test
    public void aSchemelessHeaderMatchesTheBareToken() throws Exception {
        final var credentials = new AtomicReference<String>();
        final AuthenticationManager am = (Authentication authentication) -> {
            credentials.set(authentication.getCredentials().toString());
            return new AuthenticatedToken(
                    authentication.getCredentials().toString(),
                    "principal",
                    authentication.getDetails(),
                    AuthorityUtils.NO_AUTHORITIES
            );
        };
        final var filter = new HttpHeaderAuthenticationFilter(
                am,
                new LinkedHashSet<>(List.of(HeaderAndScheme.schemeless("Jwt-Auth")))
        );
        final MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Jwt-Auth", "a-bare-token");
        final FilterChain chain = (request, response) -> {
        };

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Assertions.assertEquals("a-bare-token", credentials.get());
    }

    @Test
    public void theSchemeIsNormalisedByTheConstructorWhicheverWayItIsBuilt() {
        Assertions.assertEquals("BEARER ", new HeaderAndScheme("Authorization", "Bearer").scheme());
        Assertions.assertEquals("BEARER ", new HeaderAndScheme("Authorization", "  bearer  ").scheme());
        Assertions.assertEquals("BEARER ", new HeaderAndScheme("Authorization", "BEARER ").scheme());
        Assertions.assertEquals("", new HeaderAndScheme("Jwt-Auth", "").scheme());
        Assertions.assertEquals("", new HeaderAndScheme("Jwt-Auth", "   ").scheme());
        Assertions.assertEquals("", HeaderAndScheme.schemeless("Jwt-Auth").scheme());
    }

}
