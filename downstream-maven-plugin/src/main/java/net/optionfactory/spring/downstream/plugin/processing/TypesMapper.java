package net.optionfactory.spring.downstream.plugin.processing;

import com.palantir.javapoet.ClassName;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.optionfactory.spring.downstream.Downstream;
import net.optionfactory.spring.downstream.plugin.processing.PayloadsScanner.PayloadType;
import org.apache.maven.plugin.MojoExecutionException;

public class TypesMapper {

    private final String targetPackage;
    private final Nesting flattening;

    public enum Nesting {
        FLATTEN,
        NESTED
    }

    public TypesMapper(String targetPackage, Nesting flattening) {
        this.targetPackage = targetPackage;
        this.flattening = flattening;
    }

    public record PayloadInfo(PayloadType type, ClassName className) {

    }

    public TypesRegistry map(Map<Class<?>, PayloadType> scanResult) throws MojoExecutionException {
        final var result = new HashMap<Class<?>, PayloadInfo>();
        for (final var entry : scanResult.entrySet()) {
            result.put(entry.getKey(), new PayloadInfo(entry.getValue(), resolveClassName(entry.getKey(), scanResult)));
        }
        final var clashingGroups = result.entrySet().stream()
                .collect(Collectors.groupingBy(entry -> entry.getValue().className().canonicalName()))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .toList();
        if (!clashingGroups.isEmpty()) {
            final var message = clashingGroups.stream().map(cg -> {
                final var className = cg.getKey();
                final var collisions = cg.getValue().stream().map(e -> e.getKey().getName()).collect(Collectors.joining(", "));
                return "Target identifier '%s' caused a naming collision. Conflicting source classes: [%s]".formatted(className, collisions);
            }).collect(Collectors.joining("\n"));
            throw new MojoExecutionException("Naming collision detected while mapping types. If you are using UNPREFIXED flattening, consider switching to PREFIX to isolate nested inner classes:\n%s".formatted(message));
        }
        return new TypesRegistry(result);
    }

    private ClassName resolveClassName(Class<?> clazz, Map<Class<?>, PayloadType> allDiscovered) {
        final var annotation = clazz.getAnnotation(Downstream.Rename.class);
        final var name = annotation != null ? annotation.value() : clazz.getSimpleName();
        final Class<?> declaring = clazz.getDeclaringClass();

        if (declaring != null && allDiscovered.containsKey(declaring)) {
            if (flattening == Nesting.FLATTEN) {
                return ClassName.get(targetPackage, name);
            }
            return resolveClassName(declaring, allDiscovered).nestedClass(name);
        }
        return ClassName.get(targetPackage, name);
    }
}
