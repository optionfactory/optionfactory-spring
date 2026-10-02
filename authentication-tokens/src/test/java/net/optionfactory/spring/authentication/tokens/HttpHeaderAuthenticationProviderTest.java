package net.optionfactory.spring.authentication.tokens;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.AuthenticatedToken;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.PrincipalAndAuthorities;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.TokenProcessor;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.UnauthenticatedToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

public class HttpHeaderAuthenticationProviderTest {

    private static final HeaderAndScheme BEARER = new HeaderAndScheme("Authorization", "Bearer");
    private static final String SECRET = "a-static-secret";

    private static UnauthenticatedToken presented(String token) {
        final var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        return new UnauthenticatedToken(BEARER, token, request);
    }

    private static TokenProcessor accepting(String principal) {
        return (hs, token) -> new PrincipalAndAuthorities(principal, AuthorityUtils.createAuthorityList("ROLE_" + principal.toUpperCase()));
    }

    @Test
    public void onlyUnauthenticatedTokensAreSupported() {
        final var provider = new HttpHeaderAuthenticationProvider(List.of());

        Assertions.assertTrue(provider.supports(UnauthenticatedToken.class), "the tokens found by the filter are supported");
        Assertions.assertFalse(provider.supports(UsernamePasswordAuthenticationToken.class), "other authentications are left to their own providers");
    }

    @Test
    public void theFirstAcceptingProcessorDecides() {
        final var provider = new HttpHeaderAuthenticationProvider(List.of((hs, token) -> null, accepting("first"), accepting("second")));

        final var authentication = provider.authenticate(presented(SECRET));

        Assertions.assertEquals("first", authentication.getPrincipal(), "processors returning null are skipped and the first accepting one decides");
        Assertions.assertEquals(List.of("ROLE_FIRST"), AuthorityUtils.authorityListToSet(authentication.getAuthorities()).stream().toList(), "the authorities are those granted by the accepting processor");
    }

    @Test
    public void aTokenNoProcessorAcceptsIsNotAuthenticated() {
        final var provider = new HttpHeaderAuthenticationProvider(List.of(new TokenProcessor.StaticLax(BEARER, SECRET, new PrincipalAndAuthorities("p", List.of()))));

        Assertions.assertNull(provider.authenticate(presented("another-token")), "a token no processor accepts yields no authentication");
    }

    @Test
    public void aRejectingProcessorEndsTheSearch() {
        final var consulted = new AtomicBoolean(false);
        final var provider = new HttpHeaderAuthenticationProvider(List.of(
                new TokenProcessor.StaticStrict(BEARER, SECRET, new PrincipalAndAuthorities("strict", List.of())),
                (hs, token) -> {
                    consulted.set(true);
                    return new PrincipalAndAuthorities("later", List.of());
                }));

        Assertions.assertThrows(BadCredentialsException.class, () -> provider.authenticate(presented("another-token")), "a strict static token rejects any other token on its header");
        Assertions.assertFalse(consulted.get(), "no processor after a rejecting one is consulted");
    }

    @Test
    public void theAuthenticationCarriesTheTokenAndTheRequestDetails() {
        final var provider = new HttpHeaderAuthenticationProvider(List.of(accepting("svc")));

        final var authentication = provider.authenticate(presented(SECRET));

        Assertions.assertInstanceOf(AuthenticatedToken.class, authentication, "an accepted token yields an AuthenticatedToken");
        Assertions.assertTrue(authentication.isAuthenticated(), "an accepted token is authenticated");
        Assertions.assertEquals(SECRET, authentication.getCredentials(), "the token stays available as the credentials");
        final var details = Assertions.assertInstanceOf(WebAuthenticationDetails.class, authentication.getDetails(), "the details are the request's");
        Assertions.assertEquals("10.0.0.1", details.getRemoteAddress(), "the details carry the caller's address");
        Assertions.assertFalse(authentication.toString().contains(SECRET), "the token never shows in the string form, which gets logged");
    }
}
