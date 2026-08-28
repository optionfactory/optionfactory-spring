package net.optionfactory.spring.data.jpa.test.containers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testcontainers.lifecycle.Startable;

/// A [Startable] that only counts, no Docker involved. [Definition]s record the containers they create, keyed
/// by definition class, so tests can assert on how many were started and stopped.
public final class FakeContainer implements Startable {

    private static final Map<Class<?>, List<FakeContainer>> CREATED = new HashMap<>();

    public int starts;
    public int stops;
    public boolean running;

    @Override
    public void start() {
        starts++;
        running = true;
    }

    @Override
    public void stop() {
        stops++;
        running = false;
    }

    public static List<FakeContainer> created(Class<? extends Definition> definition) {
        return CREATED.getOrDefault(definition, List.of());
    }

    /// Subclass with a distinct class per scenario: the registry is JVM-wide.
    public abstract static class Definition implements ContainerDefinition<FakeContainer> {

        @Override
        public FakeContainer start() {
            final var container = new FakeContainer();
            container.start();
            CREATED.computeIfAbsent(getClass(), k -> new ArrayList<>()).add(container);
            return container;
        }

        @Override
        public Map<String, Object> properties(FakeContainer container) {
            return Map.of("fake.url", "fake://" + System.identityHashCode(container));
        }
    }
}
