package net.optionfactory.spring.authentication.tokens;

import java.util.List;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.PrincipalAndAuthorities;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.TokenProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StaticLaxTokenProcessorTest {

    private static final HeaderAndScheme BASIC = new HeaderAndScheme("Authorization", "BASIC ");
    private static final PrincipalAndAuthorities PAA = new PrincipalAndAuthorities("principal", List.of());

    @Test
    public void returnsAuthoritiesWhenSchemeAndTokenMatch() {
        final var lax = new TokenProcessor.StaticLax(BASIC, "secret", PAA);
        Assertions.assertSame(PAA, lax.process(BASIC, "secret"));
    }

    @Test
    public void returnsNullWhenSchemeDoesNotMatch() {
        final var lax = new TokenProcessor.StaticLax(BASIC, "secret", PAA);
        final var presented = new HeaderAndScheme("Authorization", "BEARER ");
        Assertions.assertNull(lax.process(presented, "secret"));
    }

    @Test
    public void returnsNullWhenTokenDoesNotMatch() {
        final var lax = new TokenProcessor.StaticLax(BASIC, "secret", PAA);
        Assertions.assertNull(lax.process(BASIC, "wrong"));
    }
}
