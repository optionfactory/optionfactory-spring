package net.optionfactory.spring.authentication;

import net.optionfactory.spring.authentication.Principals.PrincipalsConfigurer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PrincipalsConfigurerTest {

    public record AppPrincipal(String id) {

    }

    /// Compiles only when every `principal(...)` returns a `PrincipalsConfigurer<AppPrincipal>`: on a
    /// raw return the mappers chained after the first are raw too, and `l.longValue()` or a
    /// `PrincipalsConfigurer<AppPrincipal>` assignment would not compile.
    @Test
    public void principalMappingsChainWithTheirTypes() {
        final var configurer = Principals.coalescing(AppPrincipal.class);

        final PrincipalsConfigurer<AppPrincipal> chained = configurer
                .principal(Integer.class, (auth, i) -> new AppPrincipal(Integer.toString(i.intValue())))
                .principal(Long.class, (auth, l) -> new AppPrincipal(Long.toString(l.longValue())))
                .principal("anonymousUser", new AppPrincipal("guest"))
                .principal(new PrincipalMappingStrategy.ByInstance<>("system", (auth, principal) -> new AppPrincipal("system")));

        Assertions.assertSame(configurer, chained, "every principal(...) returns the configurer itself, typed with the application principal");
    }
}
