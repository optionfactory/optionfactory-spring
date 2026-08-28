package net.optionfactory.spring.data.jpa.test.containers;

import java.util.Map;
import org.testcontainers.lifecycle.Startable;

/// Describes a container shared by every test running in the JVM: it is started lazily, right before the first
/// test class declaring it (via [SharedContainer]) runs, and stopped once after the last test has run.
///
/// Implementations must be public with a public no-arg constructor: the implementation class is instantiated
/// reflectively and is the identity of the shared instance, so every test class declaring the same definition
/// shares the same running container.
///
/// ```java
/// public class TestPostgres implements ContainerDefinition<PostgreSQLContainer> {
///
///     @Override
///     public PostgreSQLContainer start() throws Exception {
///         final var image = DockerImageName.parse("optionfactory/debian13-postgres18:235").asCompatibleSubstituteFor("postgres");
///         final var container = new PostgreSQLContainer(image).withUsername("postgres").withDatabaseName("test");
///         container.start();
///         container.execInContainer("psql", "-U", "postgres", "-c", "ALTER USER postgres PASSWORD 'test'");
///         container.execInContainer("psql", "-U", "postgres", "-c", "CREATE DATABASE test");
///         return container;
///     }
///
///     @Override
///     public Map<String, Object> properties(PostgreSQLContainer container) {
///         return Map.of("db.jdbc.url", container.getJdbcUrl());
///     }
/// }
/// ```
///
/// @param <C> the container type: anything [Startable] (`GenericContainer`, `ComposeContainer`, ...)
public interface ContainerDefinition<C extends Startable> {

    /// Creates, starts and initializes the container (e.g. runs `execInContainer`), returning it running.
    /// Invoked at most once per JVM; the returned container is stopped by the framework after the last test.
    ///
    /// If this method throws after the container has been started, the container is left to Ryuk, which
    /// removes it when the JVM exits.
    ///
    /// @return the running container
    /// @throws Exception if the container cannot be started or initialized
    C start() throws Exception;

    /// Configuration to expose to the code under test once the container is running, e.g. connection
    /// coordinates. With Spring these are added to the `Environment` of the test context with the highest
    /// precedence, the same as `@DynamicPropertySource`.
    ///
    /// @param container the running container
    /// @return the properties to expose, empty by default
    default Map<String, Object> properties(C container) {
        return Map.of();
    }
}
