package net.optionfactory.spring.data.jpa.test.containers;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import net.optionfactory.spring.data.jpa.test.Jupiter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Runs the fixture classes below through the launcher and asserts on the fake containers from the outside.
/// Being static nested classes, the fixtures are not discovered by Surefire.
public class SharedContainersExtensionTest {

    @Test
    public void containerIsStartedOnceForAllClassesAndStoppedAfterTheLastTest() {
        final var summary = Jupiter.run(FirstUser.class, NoContainers.class, SecondUser.class);

        Assertions.assertEquals(3, summary.getTestsSucceededCount());
        final var container = FakeContainer.created(Shared.class);
        Assertions.assertEquals(1, container.size());
        Assertions.assertEquals(1, container.get(0).starts);
        Assertions.assertEquals(1, container.get(0).stops);
    }

    @Test
    public void declarationsAreFoundThroughComposedAnnotationsSuperclassesAndNestedClasses() {
        final var summary = Jupiter.run(InheritingUser.class);

        Assertions.assertEquals(1, summary.getTestsSucceededCount());
        for (final var definition : List.of(ComposedA.class, ComposedB.class)) {
            Assertions.assertEquals(1, FakeContainer.created(definition).size(), definition.getSimpleName());
            Assertions.assertEquals(1, FakeContainer.created(definition).get(0).stops, definition.getSimpleName());
        }
    }

    /* fixtures */
    public static class Shared extends FakeContainer.Definition {
    }

    public static class ComposedA extends FakeContainer.Definition {
    }

    public static class ComposedB extends FakeContainer.Definition {
    }

    @SharedContainer(Shared.class)
    public static class FirstUser {

        @Test
        public void containerIsRunning() {
            Assertions.assertTrue(FakeContainer.created(Shared.class).get(0).running);
        }
    }

    @SharedContainer(Shared.class)
    public static class SecondUser extends FirstUser {
    }

    public static class NoContainers {

        @Test
        public void nothing() {
        }
    }

    @SharedContainer(ComposedA.class)
    @SharedContainer(ComposedB.class)
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Composed {
    }

    @Composed
    public abstract static class Base {
    }

    public static class InheritingUser extends Base {

        @Nested
        public class Inner {

            @Test
            public void bothContainersAreRunning() {
                Assertions.assertTrue(FakeContainer.created(ComposedA.class).get(0).running);
                Assertions.assertTrue(FakeContainer.created(ComposedB.class).get(0).running);
            }
        }
    }
}
