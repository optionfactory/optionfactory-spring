package net.optionfactory.spring.downstream.plugin.mapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.downstream.Downstream;

/// Assigns the generated name of every payload type, and checks that no two of them collide.
///
/// A type is named after its simple name, or its `@Downstream.Rename` value. A nested type whose
/// outer type is a payload too is then named according to the [Nesting]; one whose outer type is
/// not is a top-level type. Every type lives in the same target package.
///
/// The collision check compares the [flat names][TargetName#flatName] whatever the nesting, so
/// `Outer.Inner` and a top-level `OuterInner` collide even with [Nesting#NESTED], where the
/// generated Java would not.
public class TypeRegistry {

    /// The generated name of a payload type.
    ///
    /// @param packageName the package of the generated type
    /// @param names the simple names from the top-level type down to this one: a single name for
    /// a top-level type, one per nesting level with [Nesting#NESTED]
    public record TargetName(String packageName, List<String> names) {

        /// @return the name of the top-level type containing this one, the type itself when it is
        /// top-level
        public String topLevelName() {
            return names.get(0);
        }

        /// @return the names joined without separator, e.g. `OuterInner`: the name used by outputs
        /// without nested types
        public String flatName() {
            return String.join("", names);
        }
    }

    /// How a nested payload type whose outer type is a payload too is named.
    public enum Nesting {

        /// Ignores the outer types: `Parent.Child` becomes a top-level `Child`. Nested types with
        /// the same simple name in different outer types collide.
        FLATTEN,
        /// Keeps the hierarchy: in Java `Parent.Child` stays a static nested type of `Parent`.
        /// Outputs without nested types, such as TypeScript, use the flat name `ParentChild`.
        NESTED,
        /// Moves nested types to the top level, prefixed with their outer type names:
        /// `Parent.Child` becomes `ParentChild`.
        PREFIXED
    }

    private final Map<Class<?>, TargetName> dictionary = new HashMap<>();

    /// @param rawPayloads the payload types to name
    /// @param targetPackage the package of every generated type
    /// @param nesting how nested types are named
    /// @throws IllegalStateException when two types end up with the same flat name, listing the
    /// colliding source classes
    public TypeRegistry(Set<Class<?>> rawPayloads, String targetPackage, Nesting nesting) {
        for (final Class<?> sourceClass : rawPayloads) {
            dictionary.put(sourceClass, resolveName(sourceClass, targetPackage, nesting, rawPayloads));
        }
        verifyNoCollisions();
    }

    /// @param sourceClass a source class
    /// @return its generated name, `null` when it is not a registered payload
    public TargetName getTargetName(Class<?> sourceClass) {
        return dictionary.get(sourceClass);
    }

    /// @param className the binary (`Outer$Inner`) or canonical (`Outer.Inner`) name of a source
    /// class
    /// @return its generated name, `null` when no registered payload has that name
    public TargetName getTargetName(String className) {
        return allSourceClasses().stream()
                .filter(c -> matchesName(c, className))
                .findFirst()
                .map(this::getTargetName)
                .orElse(null);
    }

    /// @return the registered payload types, in no particular order
    public Collection<Class<?>> allSourceClasses() {
        return dictionary.keySet();
    }

    /// @param clazz a class, possibly `null`
    /// @return true when it is a registered payload, false for `null`
    public boolean isRegistered(Class<?> clazz) {
        return clazz != null && dictionary.containsKey(clazz);
    }

    /// @param className the binary or canonical name of a class
    /// @return true when it is the name of a registered payload
    public boolean isRegistered(String className) {
        return allSourceClasses().stream()
                .anyMatch(c -> matchesName(c, className));
    }

    private boolean matchesName(Class<?> clazz, String className) {
        return clazz.getName().equals(className) || 
               (clazz.getCanonicalName() != null && clazz.getCanonicalName().equals(className));
    }

    private TargetName resolveName(Class<?> clazz, String targetPackage, Nesting nesting, Set<Class<?>> allDiscovered) {
        final var annotation = clazz.getAnnotation(Downstream.Rename.class);
        final String name = annotation != null ? annotation.value() : clazz.getSimpleName();
        final Class<?> declaring = clazz.getDeclaringClass();
        if (declaring != null && allDiscovered.contains(declaring)) {
            final TargetName parentName = resolveName(declaring, targetPackage, nesting, allDiscovered);

            return switch (nesting) {
                case PREFIXED ->
                    new TargetName(targetPackage, List.of(parentName.flatName() + name));
                case NESTED -> {
                    final List<String> nestedNames = new ArrayList<>(parentName.names());
                    nestedNames.add(name);
                    yield new TargetName(targetPackage, nestedNames);
                }
                case FLATTEN ->
                    new TargetName(targetPackage, List.of(name));
            };
        }
        return new TargetName(targetPackage, List.of(name));
    }

    private void verifyNoCollisions() {
        final var collisions = dictionary.entrySet().stream()
                .collect(Collectors.groupingBy(e -> e.getValue().flatName()))
                .entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .toList();

        if (!collisions.isEmpty()) {
            final String message = collisions.stream().map(cg -> {
                final String targetName = cg.getKey();
                final String sources = cg.getValue().stream()
                        .map(e -> e.getKey().getName())
                        .collect(Collectors.joining(", "));
                return "Target identifier '%s' caused a naming collision. Conflicting source classes: [%s]".formatted(targetName, sources);
            }).collect(Collectors.joining("\n"));

            throw new IllegalStateException("Naming collision detected while mapping types: " + message);
        }
    }
}