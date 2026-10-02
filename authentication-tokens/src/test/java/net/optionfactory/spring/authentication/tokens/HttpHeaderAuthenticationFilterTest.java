package net.optionfactory.spring.authentication.tokens;

import jakarta.servlet.FilterChain;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.AuthenticatedToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

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

        Assertions.assertEquals("a-bare-token", credentials.get(), "a schemeless header yields its whole value as the token");
    }

    /// Upper-casing `ß` yields `SS`: a value upper-cased as a whole no longer lines up with the original,
    /// so the token was cut from the wrong offset.
    @Test
    public void theTokenIsCutWhereTheSchemeEndsInTheOriginalValue() throws Exception {
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
                new LinkedHashSet<>(List.of(new HeaderAndScheme("X-Auth", "Weiss")))
        );
        final FilterChain chain = (request, response) -> {
        };

        final MockHttpServletRequest mismatched = new MockHttpServletRequest();
        mismatched.addHeader("X-Auth", "weiß the-token");
        filter.doFilter(mismatched, new MockHttpServletResponse(), chain);
        Assertions.assertNull(credentials.get(), "weiß does not match the Weiss scheme, so no token is found");

        final MockHttpServletRequest matching = new MockHttpServletRequest();
        matching.addHeader("X-Auth", "weiss the-token");
        filter.doFilter(matching, new MockHttpServletResponse(), chain);
        Assertions.assertEquals("the-token", credentials.get(), "the token is cut where the scheme ends in the original value");
    }

    @Test
    public void aRequestCarryingTwoTokensProceedsUnauthenticated() throws Exception {
        final var attempted = new AtomicBoolean(false);
        final AuthenticationManager am = (Authentication authentication) -> {
            attempted.set(true);
            return new AuthenticatedToken(
                    authentication.getCredentials().toString(),
                    "principal",
                    authentication.getDetails(),
                    AuthorityUtils.NO_AUTHORITIES
            );
        };
        final var filter = new HttpHeaderAuthenticationFilter(
                am,
                new LinkedHashSet<>(List.of(new HeaderAndScheme("Authorization", "Bearer"), HeaderAndScheme.schemeless("Jwt-Auth")))
        );
        final MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer one-token");
        req.addHeader("Jwt-Auth", "another-token");
        final var proceeded = new AtomicBoolean(false);
        final FilterChain chain = (request, response) -> {
            proceeded.set(true);
            Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication(), "an ambiguous request reaches the chain unauthenticated");
        };

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Assertions.assertTrue(proceeded.get(), "an ambiguous request still proceeds down the chain");
        Assertions.assertFalse(attempted.get(), "no token of an ambiguous request is authenticated");
    }

    @Test
    public void theSchemeIsNormalisedByTheConstructorWhicheverWayItIsBuilt() {
        Assertions.assertEquals("BEARER ", new HeaderAndScheme("Authorization", "Bearer").scheme(), "the scheme is upper-cased and given its separating space");
        Assertions.assertEquals("BEARER ", new HeaderAndScheme("Authorization", "  bearer  ").scheme(), "the scheme is trimmed before the separator is added");
        Assertions.assertEquals("BEARER ", new HeaderAndScheme("Authorization", "BEARER ").scheme(), "an already normalised scheme is unchanged");
        Assertions.assertEquals("", new HeaderAndScheme("Jwt-Auth", "").scheme(), "an empty scheme gets no separator");
        Assertions.assertEquals("", new HeaderAndScheme("Jwt-Auth", "   ").scheme(), "a blank scheme is an empty one");
        Assertions.assertEquals("", HeaderAndScheme.schemeless("Jwt-Auth").scheme(), "a schemeless header has an empty scheme");
    }

    @AfterEach
    public void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void aRejectedTokenLeavesEarlierAuthenticationInPlace() throws Exception {
        final AuthenticationManager am = (Authentication authentication) -> {
            throw new BadCredentialsException("no");
        };
        final var filter = new HttpHeaderAuthenticationFilter(
                am,
                new LinkedHashSet<>(List.of(new HeaderAndScheme("Authorization", "Bearer")))
        );
        final var earlier = new TestingAuthenticationToken("session-user", "none", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(earlier);
        final MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer stale-or-forged");
        final var proceeded = new AtomicBoolean(false);
        final FilterChain chain = (request, response) -> {
            proceeded.set(true);
            Assertions.assertSame(earlier, SecurityContextHolder.getContext().getAuthentication(),
                    "a rejected token must not strip authentication established earlier in the chain");
        };

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Assertions.assertTrue(proceeded.get(), "a rejected token does not stop the request");
    }

    @Test
    public void anAmbiguousRequestLeavesEarlierAuthenticationInPlace() throws Exception {
        final var filter = new HttpHeaderAuthenticationFilter(
                (Authentication authentication) -> new AuthenticatedToken(
                        authentication.getCredentials().toString(),
                        "principal",
                        authentication.getDetails(),
                        AuthorityUtils.NO_AUTHORITIES
                ),
                new LinkedHashSet<>(List.of(new HeaderAndScheme("Authorization", "Bearer"), HeaderAndScheme.schemeless("Jwt-Auth")))
        );
        final var earlier = new TestingAuthenticationToken("session-user", "none", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(earlier);
        final MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer one-token");
        req.addHeader("Jwt-Auth", "another-token");
        final var proceeded = new AtomicBoolean(false);
        final FilterChain chain = (request, response) -> {
            proceeded.set(true);
            Assertions.assertSame(earlier, SecurityContextHolder.getContext().getAuthentication(),
                    "an ambiguous token set must not strip authentication established earlier in the chain");
        };

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Assertions.assertTrue(proceeded.get(), "an ambiguous request does not stop the request");
    }

    @Test
    public void anAcceptedTokenAuthenticatesTheRequest() throws Exception {
        final var filter = new HttpHeaderAuthenticationFilter(
                (Authentication authentication) -> new AuthenticatedToken(
                        authentication.getCredentials().toString(),
                        "principal",
                        authentication.getDetails(),
                        AuthorityUtils.NO_AUTHORITIES
                ),
                new LinkedHashSet<>(List.of(new HeaderAndScheme("Authorization", "Bearer")))
        );
        final MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer   the-token  ");
        final var seen = new AtomicReference<Authentication>();
        final FilterChain chain = (request, response) -> seen.set(SecurityContextHolder.getContext().getAuthentication());

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Assertions.assertNotNull(seen.get(), "the rest of the chain runs with the token's authentication");
        Assertions.assertEquals("principal", seen.get().getPrincipal(), "the authentication is the one the manager returned");
        Assertions.assertEquals("the-token", seen.get().getCredentials(), "the token is trimmed of the whitespace around it");
    }

    @Test
    public void aHeaderCarryingAnotherSchemeIsNotATokenOfOurs() throws Exception {
        final var attempted = new AtomicBoolean(false);
        final AuthenticationManager am = (Authentication authentication) -> {
            attempted.set(true);
            return authentication;
        };
        final var filter = new HttpHeaderAuthenticationFilter(
                am,
                new LinkedHashSet<>(List.of(new HeaderAndScheme("Authorization", "Bearer")))
        );
        final MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        final var proceeded = new AtomicBoolean(false);

        filter.doFilter(req, new MockHttpServletResponse(), (request, response) -> proceeded.set(true));

        Assertions.assertFalse(attempted.get(), "a basic credential is not a bearer token and is not authenticated here");
        Assertions.assertTrue(proceeded.get(), "the request proceeds for other mechanisms to handle");
    }

}
