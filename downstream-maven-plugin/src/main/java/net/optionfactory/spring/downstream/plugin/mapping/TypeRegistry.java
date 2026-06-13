package net.optionfactory.spring.downstream.plugin.mapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.downstream.Downstream;

public class TypeRegistry {

    public record TargetName(String packageName, List<String> names) {

        public String topLevelName() {
            return names.get(0);
        }

        public String flatName() {
            return String.join("", names);
        }
    }

    public enum Nesting {

        /**
         * Ignores the outer class hierarchy completely. Output is generated in
         * the output package using its simple name only. Example:
         * {@code Parent.Child} becomes a top-level class named {@code Child}.
         */
        FLATTEN,
        /**
         * Preserves the structural hierarchy, generating inner classes as
         * actual static nested classes inside their parent (supported in Java
         * output). Example: {@code Parent.Child} remains nested inside
         * {@code Parent}.
         */
        NESTED,
        /**
         * Flattens inner classes into top-level classes by prefixing them with
         * their outer class names. Example: {@code Parent.Child} becomes a
         * top-level class named {@code ParentChild}.
         */
        PREFIXED
    }

    private final Map<Class<?>, TargetName> dictionary = new HashMap<>();

    public TypeRegistry(Set<Class<?>> rawPayloads, String targetPackage, Nesting nesting) {
        for (final Class<?> sourceClass : rawPayloads) {
            dictionary.put(sourceClass, resolveName(sourceClass, targetPackage, nesting, rawPayloads));
        }
        verifyNoCollisions();
    }

    public TargetName getTargetName(Class<?> sourceClass) {
        return dictionary.get(sourceClass);
    }

    public Collection<Class<?>> allSourceClasses() {
        return dictionary.keySet();
    }

    public boolean isRegistered(Class<?> clazz) {
        return clazz != null && dictionary.containsKey(clazz);
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
