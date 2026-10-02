package net.optionfactory.spring.downstream.plugin;

import java.util.List;
import java.util.Set;
import net.optionfactory.spring.downstream.Downstream;
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
                -> new TypeRegistry(clashingPayloads, "net.generated", Nesting.FLATTEN),
                "two nested types with the same simple name collide when flattened"
        );
        Assertions.assertTrue(ex.getMessage().contains("naming collision"), "the registry reports the flattened naming collision");
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

        Assertions.assertEquals("OuterInnerDeeplyNested", target.flatName(), "PREFIXED accumulates every outer name recursively");
        Assertions.assertEquals(1, target.names().size(), "PREFIXED yields a top-level name");
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
                () -> Assertions.assertEquals(2, target.names().size(), "NESTED keeps one name per nesting level"),
                () -> Assertions.assertEquals("Outer", target.names().get(0), "NESTED keeps the outer name first"),
                () -> Assertions.assertEquals("Inner", target.names().get(1), "NESTED keeps the inner name last")
        );
    }

    @Downstream.Rename("Root")
    public static class Renamed {

        public static class Child {
        }
    }

    public static class OuterInner {
    }

    @Test
    public void renamedOuterTypesLendTheNewNameToTheirPrefixedNestedTypes() {
        final var registry = new TypeRegistry(Set.of(Renamed.class, Renamed.Child.class), "net.generated", Nesting.PREFIXED);
        Assertions.assertEquals(List.of("Root"), registry.getTargetName(Renamed.class).names(), "@Downstream.Rename replaces the simple name");
        Assertions.assertEquals(List.of("RootChild"), registry.getTargetName(Renamed.Child.class).names(), "the prefix of a nested type is the renamed outer name");
    }

    @Test
    public void flattenDropsTheOuterNamesAndNestedTypesOfUnregisteredOutersAreTopLevel() {
        final var flattened = new TypeRegistry(Set.of(Outer.class, Outer.Inner.class), "net.generated", Nesting.FLATTEN);
        Assertions.assertEquals(List.of("Inner"), flattened.getTargetName(Outer.Inner.class).names(), "FLATTEN keeps the simple name only");

        final var orphan = new TypeRegistry(Set.of(Outer.Inner.class), "net.generated", Nesting.NESTED);
        Assertions.assertEquals(List.of("Inner"), orphan.getTargetName(Outer.Inner.class).names(), "a nested type whose outer type is not a payload is top-level whatever the nesting");
        Assertions.assertEquals("net.generated", orphan.getTargetName(Outer.Inner.class).packageName(), "every type is generated in the target package");
    }

    @Test
    public void collisionsAreCheckedOnTheFlatNameEvenWhenNesting() {
        final var payloads = Set.of(Outer.class, Outer.Inner.class, OuterInner.class);
        final var ex = Assertions.assertThrows(IllegalStateException.class, () -> new TypeRegistry(payloads, "net.generated", Nesting.NESTED), "Outer.Inner and OuterInner share the flat name OuterInner");
        Assertions.assertTrue(ex.getMessage().contains(OuterInner.class.getName()), "the message names the colliding source classes");
    }

    @Test
    public void typesCanBeLookedUpByBinaryOrCanonicalName() {
        final var registry = new TypeRegistry(Set.of(Outer.class, Outer.Inner.class), "net.generated", Nesting.NESTED);
        Assertions.assertTrue(registry.isRegistered(Outer.Inner.class.getName()), "the binary name ($) finds the type");
        Assertions.assertTrue(registry.isRegistered(Outer.Inner.class.getCanonicalName()), "the canonical name (.) finds the type");
        Assertions.assertEquals(registry.getTargetName(Outer.Inner.class), registry.getTargetName(Outer.Inner.class.getCanonicalName()), "lookups by class and by name agree");
    }

    @Test
    public void unknownTypesAreNotRegisteredAndHaveNoTargetName() {
        final var registry = new TypeRegistry(Set.of(Outer.class), "net.generated", Nesting.NESTED);
        Assertions.assertFalse(registry.isRegistered((Class<?>) null), "a null class is not registered");
        Assertions.assertFalse(registry.isRegistered("com.example.Missing"), "an unknown name is not registered");
        Assertions.assertNull(registry.getTargetName(String.class), "an unregistered class has no target name");
        Assertions.assertNull(registry.getTargetName("com.example.Missing"), "an unknown name has no target name");
    }
}
