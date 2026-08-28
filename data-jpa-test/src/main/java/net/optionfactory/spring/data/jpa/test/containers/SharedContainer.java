package net.optionfactory.spring.data.jpa.test.containers;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/// Declares a [container][ContainerDefinition] a test class depends on. Repeatable: declare one per container.
///
/// Each declared container is started at most once per JVM, before the first annotated test class runs, and
/// stopped after the last test has run (when the JUnit engine's root context closes); tests that don't declare
/// it never pay for it. Can be used directly or as a meta-annotation on a project specific composed annotation,
/// and is inherited by subclasses and `@Nested` classes.
///
/// ```java
/// @SharedContainer(TestPostgres.class)
/// @SharedContainer(TestRabbit.class)
/// @SpringJUnitConfig(DatabaseConfig.class)
/// @Target(ElementType.TYPE)
/// @Retention(RetentionPolicy.RUNTIME)
/// public @interface IntegrationTest {
/// }
/// ```
///
/// The running container is available through [SharedContainerRegistry#get(Class)]; with Spring, the
/// definition's [properties][ContainerDefinition#properties(org.testcontainers.lifecycle.Startable)] are
/// automatically exposed to the test `ApplicationContext` environment by
/// [SharedContainersContextCustomizerFactory].
///
/// One container is started per forked JVM: with Surefire `forkCount > 1` each fork starts its own.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(SharedContainer.SharedContainers.class)
@Inherited
@Documented
@ExtendWith(SharedContainersExtension.class)
public @interface SharedContainer {

    /// @return the definition of the container the annotated test class depends on
    Class<? extends ContainerDefinition<?>> value();

    /// Container annotation for repeated [SharedContainer] declarations; use `@SharedContainer` directly, once
    /// per container.
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    @ExtendWith(SharedContainersExtension.class)
    @interface SharedContainers {

        /// @return the repeated declarations
        SharedContainer[] value();

    }

}
