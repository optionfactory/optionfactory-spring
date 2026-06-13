package net.optionfactory.spring.downstream.plugin;

import java.util.Set;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TypeRegistryTest {

    public static class ScopeA {

        public static class Target {
        }
    }

    public static class ScopeB {

        public static class Target {
        }
    }

    public static class Outer {

        public static class Inner {

            public static class DeeplyNested {
            }
        }
    }

    @Test
    public void shouldThrowExceptionOnFlattenNamingCollisions() {
        final var clashingPayloads = Set.of(
                ScopeA.Target.class,
                ScopeB.Target.class
        );

        final var ex = Assertions.assertThrows(IllegalStateException.class, ()
                -> new TypeRegistry(clashingPayloads, "net.generated", Nesting.FLATTEN)
        );
        Assertions.assertTrue(ex.getMessage().contains("naming collision"), "Registry failed to catch flattened namespace collisions");
    }

    @Test
    public void shouldPrefixInnerClassesRecursivelyUpToRootEmittedDto() {
        final var payloads = Set.of(
                Outer.class,
                Outer.Inner.class,
                Outer.Inner.DeeplyNested.class
        );
        final var registry = new TypeRegistry(payloads, "net.generated", Nesting.PREFIXED);

        final var target = registry.getTargetName(Outer.Inner.DeeplyNested.class);

        Assertions.assertEquals("OuterInnerDeeplyNested", target.flatName(), "PREFIXED strategy did not recursively accumulate parent names");
        Assertions.assertEquals(1, target.names().size(), "PREFIXED strategy should yield a top-level file footprint");
    }

    @Test
    public void shouldRetainStructuralArraySizeInNestedStrategy() {
        final var payloads = Set.of(
                Outer.class,
                Outer.Inner.class
        );
        final var registry = new TypeRegistry(payloads, "net.generated", Nesting.NESTED);
        final var target = registry.getTargetName(Outer.Inner.class);
        Assertions.assertAll(
                () -> Assertions.assertEquals(2, target.names().size()),
                () -> Assertions.assertEquals("Outer", target.names().get(0)),
                () -> Assertions.assertEquals("Inner", target.names().get(1))
        );

    }
}
