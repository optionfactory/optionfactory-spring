package net.optionfactory.spring.data.jpa.test.containers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.lifecycle.Startable;

/// JVM-wide registry of the shared containers, keyed by [ContainerDefinition] class.
///
/// Containers are started lazily on first access and stopped by [SharedContainersExtension] when the JUnit
/// root context closes; a subsequent access (e.g. another test plan in the same JVM) starts a fresh one.
/// Access is thread-safe, so it works with JUnit parallel execution.
public final class SharedContainerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(SharedContainerRegistry.class);
    private static final ConcurrentMap<Class<? extends ContainerDefinition<?>>, Holder<?>> HOLDERS = new ConcurrentHashMap<>();

    private SharedContainerRegistry() {
    }

    /// Returns the running container for the given definition, starting it if needed.
    ///
    /// @param <C> the container type
    /// @param definition the definition class, as declared in [SharedContainer#value()]
    /// @return the running container
    /// @throws IllegalStateException if the definition cannot be instantiated or the container fails to start
    @SuppressWarnings("unchecked")
    public static <C extends Startable> C get(Class<? extends ContainerDefinition<C>> definition) {
        return (C) holder(definition).container();
    }

    /// Returns the [properties][ContainerDefinition#properties(Startable)] of the running container for the
    /// given definition, starting it if needed.
    ///
    /// @param definition the definition class, as declared in [SharedContainer#value()]
    /// @return the properties exposed by the definition
    public static Map<String, Object> properties(Class<? extends ContainerDefinition<?>> definition) {
        return holder(definition).properties();
    }

    static Holder<?> holder(Class<? extends ContainerDefinition<?>> definition) {
        return HOLDERS.computeIfAbsent(definition, Holder::new);
    }

    /// Lifecycle of a single shared container. Implements [AutoCloseable] so it can be put in a JUnit
    /// `ExtensionContext.Store`, which closes it when the owning context closes; closing a holder stops the
    /// container, and a later access starts a fresh one.
    static final class Holder<C extends Startable> implements AutoCloseable {

        private final Class<? extends ContainerDefinition<C>> type;
        private final ContainerDefinition<C> definition;
        private C container;

        @SuppressWarnings("unchecked")
        Holder(Class<? extends ContainerDefinition<?>> type) {
            this.type = (Class<? extends ContainerDefinition<C>>) type;
            this.definition = instantiate(this.type);
        }

        synchronized C container() {
            if (container == null) {
                LOGGER.info("Starting shared container {}", type.getName());
                try {
                    container = definition.start();
                } catch (Exception ex) {
                    throw new IllegalStateException("failed to start shared container " + type.getName(), ex);
                }
            }
            return container;
        }

        synchronized Map<String, Object> properties() {
            return definition.properties(container());
        }

        @Override
        public synchronized void close() {
            if (container == null) {
                return;
            }
            LOGGER.info("Stopping shared container {}", type.getName());
            try {
                container.stop();
            } finally {
                container = null;
            }
        }

        private static <C extends Startable> ContainerDefinition<C> instantiate(Class<? extends ContainerDefinition<C>> type) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("ContainerDefinition " + type.getName() + " must be public with a public no-arg constructor", ex);
            }
        }
    }
}
