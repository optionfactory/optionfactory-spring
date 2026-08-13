package net.optionfactory.spring.authentication.tokens;

import java.util.List;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.PrincipalAndAuthorities;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.TokenProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

public class StaticStrictTokenProcessorTest {

    private static final HeaderAndScheme BASIC = new HeaderAndScheme("Authorization", "BASIC ");
    private static final PrincipalAndAuthorities PAA = new PrincipalAndAuthorities("principal", List.of());

    @Test
    public void returnsNullWhenSchemeDoesNotMatch() {
        final var strict = new TokenProcessor.StaticStrict(BASIC, "secret", PAA);
        final var presented = new HeaderAndScheme("Authorization", "BEARER ");
        Assertions.assertNull(strict.process(presented, "anything"),
                "a non-matching scheme must not authenticate as the strict principal");
    }

    @Test
    public void returnsAuthoritiesWhenSchemeAndTokenMatch() {
        final var strict = new TokenProcessor.StaticStrict(BASIC, "secret", PAA);
        Assertions.assertSame(PAA, strict.process(BASIC, "secret"));
    }

    @Test
    public void throwsWhenSchemeMatchesButTokenDoesNot() {
        final var strict = new TokenProcessor.StaticStrict(BASIC, "secret", PAA);
        Assertions.assertThrows(BadCredentialsException.class, () -> strict.process(BASIC, "wrong"));
    }
}
