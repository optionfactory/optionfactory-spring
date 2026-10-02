package net.optionfactory.spring.authentication;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class CoalescingAuthenticationTest {

    public record AppPrincipal(String id) {

    }

    private static TestingAuthenticationToken source(String name) {
        final var source = new TestingAuthenticationToken(name, "credentials", "ROLE_USER");
        source.setDetails("details");
        return source;
    }

    @Test
    public void onlyThePrincipalIsReplaced() {
        final var source = source("someone");
        final var coalesced = new CoalescingAuthentication(source, new AppPrincipal("app-id"));

        Assertions.assertEquals(new AppPrincipal("app-id"), coalesced.getPrincipal(), "the principal is the mapped one");
        Assertions.assertEquals(List.of("ROLE_USER"), coalesced.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(), "the authorities are the source's");
        Assertions.assertEquals("credentials", coalesced.getCredentials(), "the credentials are the source's");
        Assertions.assertEquals("details", coalesced.getDetails(), "the details are the source's");
        Assertions.assertEquals(source.isAuthenticated(), coalesced.isAuthenticated(), "the authenticated flag is the source's");
    }

    @Test
    public void theNameIsTheOneTheMechanismAssigned() {
        final var coalesced = new CoalescingAuthentication(source("someone"), new AppPrincipal("app-id"));

        Assertions.assertEquals("someone", coalesced.getName(), "the name comes from the source, not from the mapped principal");
    }

    @Test
    public void clearingTheAuthenticatedFlagClearsItOnTheSource() {
        final var source = source("someone");
        final var coalesced = new CoalescingAuthentication(source, new AppPrincipal("app-id"));

        coalesced.setAuthenticated(false);

        Assertions.assertFalse(source.isAuthenticated(), "the flag is held by the source");
        Assertions.assertFalse(coalesced.isAuthenticated(), "the flag is read back from the source");
    }

    @Test
    public void equalityIsDecidedByThePrincipalAlone() {
        final var a = new CoalescingAuthentication(source("someone"), new AppPrincipal("app-id"));
        final var b = new CoalescingAuthentication(source("someone-else"), new AppPrincipal("app-id"));
        final var c = new CoalescingAuthentication(source("someone"), new AppPrincipal("another-id"));

        Assertions.assertEquals(a, b, "equal principals make equal authentications whatever their sources");
        Assertions.assertEquals(a.hashCode(), b.hashCode(), "equal authentications share their hash code");
        Assertions.assertNotEquals(a, c, "different principals make different authentications");
    }

    @Test
    public void itsStringFormDoesNotRevealTheCredentials() {
        final var coalesced = new CoalescingAuthentication(new TestingAuthenticationToken("someone", "a-secret-password", "ROLE_USER"), new AppPrincipal("app-id"));

        Assertions.assertFalse(coalesced.toString().contains("a-secret-password"), "toString, which gets logged, must not carry the credentials");
    }
}
