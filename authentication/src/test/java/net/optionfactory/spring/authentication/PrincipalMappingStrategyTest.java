package net.optionfactory.spring.authentication;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PrincipalMappingStrategyTest {

    @Test
    public void byTypeSupportsSubtypes() {
        final var strategy = new PrincipalMappingStrategy.ByType<CharSequence, String>(CharSequence.class, (auth, cs) -> cs.toString());

        Assertions.assertTrue(strategy.supports(null, "a string"), "an instance of a subtype is supported");
        Assertions.assertTrue(strategy.supports(null, new StringBuilder("a builder")), "any implementation of the type is supported");
        Assertions.assertFalse(strategy.supports(null, 42), "an instance of an unrelated type is not supported");
    }

    @Test
    public void byInstanceSupportsEqualPrincipalsOnly() {
        final var strategy = new PrincipalMappingStrategy.ByInstance<String>("anonymousUser", (auth, p) -> "guest");

        Assertions.assertTrue(strategy.supports(null, new String("anonymousUser")), "an equal, not necessarily identical, principal is supported");
        Assertions.assertFalse(strategy.supports(null, "someone"), "a different principal is not supported");
        Assertions.assertEquals("guest", strategy.map(null, "anonymousUser"), "the supported principal is mapped by the configured mapper");
    }
}
