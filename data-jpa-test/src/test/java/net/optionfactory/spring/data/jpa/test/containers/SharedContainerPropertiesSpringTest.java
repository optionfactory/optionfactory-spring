package net.optionfactory.spring.data.jpa.test.containers;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/// End to end through Spring: the customizer is picked up from `META-INF/spring.factories` and the
/// definition's properties are visible to the context.
@SharedContainer(SharedContainerPropertiesSpringTest.Postgresish.class)
@SpringJUnitConfig(SharedContainerPropertiesSpringTest.Config.class)
public class SharedContainerPropertiesSpringTest {

    public static class Postgresish extends FakeContainer.Definition {
    }

    @Configuration
    public static class Config {
    }

    private final String url;

    @Inject
    public SharedContainerPropertiesSpringTest(@Value("${fake.url}") String url) {
        this.url = url;
    }

    @Test
    public void containerPropertiesAreExposedToTheContext() {
        final var container = SharedContainerRegistry.get(Postgresish.class);

        Assertions.assertTrue(container.running);
        Assertions.assertEquals("fake://" + System.identityHashCode(container), url);
    }
}
