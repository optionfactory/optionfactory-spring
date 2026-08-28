package net.optionfactory.spring.data.jpa.test.containers;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;

/// Exposes the [properties][ContainerDefinition#properties(org.testcontainers.lifecycle.Startable)] of the
/// containers declared with [SharedContainer] to the test `ApplicationContext` environment, with the same
/// precedence as `@DynamicPropertySource` (i.e. above `@TestPropertySource`).
///
/// Registered through `META-INF/spring.factories`, so it is active whenever this module is on the test
/// classpath; the customizer takes part in the context cache key, so tests declaring different containers get
/// different contexts. Containers are started on context load if the [SharedContainersExtension] did not
/// already start them.
public class SharedContainersContextCustomizerFactory implements ContextCustomizerFactory {

    ///
    /// @param testClass the test class
    /// @param configAttributes the attributes
    /// @return a customizer for the containers declared on the test class (or its hierarchy / enclosing classes),
    /// or `null` when the class declares none
    @Override
    @SuppressWarnings("unchecked")
    public @Nullable ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        final var definitions = MergedAnnotations.search(SearchStrategy.TYPE_HIERARCHY)
                .withEnclosingClasses(TestContextAnnotationUtils::searchEnclosingClass)
                .from(testClass)
                .stream(SharedContainer.class)
                .<Class<? extends ContainerDefinition<?>>>map(annotation -> (Class<? extends ContainerDefinition<?>>) annotation.getClass("value"))
                .distinct()
                .toList();
        if (definitions.isEmpty()) {
            return null;
        }
        return new SharedContainersContextCustomizer(definitions);
    }

    /// Adds one `MapPropertySource` per definition exposing properties, first in the environment.
    record SharedContainersContextCustomizer(List<Class<? extends ContainerDefinition<?>>> definitions) implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            final var sources = context.getEnvironment().getPropertySources();
            for (final var definition : definitions) {
                final var properties = SharedContainerRegistry.properties(definition);
                if (properties.isEmpty()) {
                    continue;
                }
                sources.addFirst(new MapPropertySource("sharedContainers." + definition.getName(), properties));
            }
        }
    }
}
