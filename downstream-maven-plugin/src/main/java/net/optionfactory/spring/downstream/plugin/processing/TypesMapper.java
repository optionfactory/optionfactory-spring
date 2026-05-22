package net.optionfactory.spring.downstream.plugin.processing;

import com.palantir.javapoet.ClassName;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.optionfactory.spring.downstream.Downstream;
import org.apache.maven.plugin.MojoExecutionException;

public class TypesMapper {

    private final String targetPackage;

    public TypesMapper(String targetPackage) {
        this.targetPackage = targetPackage;
    }

    public enum Type {
        ENUM,
        DTO;
    }

    public record TypeAndName(Type type, ClassName className) {

    }

    public Map<Class<?>, TypeAndName> map(Map<Class<?>, Type> scanResult) throws MojoExecutionException {
        final Map<Class<?>, TypeAndName> result = new HashMap<>();

        for (Map.Entry<Class<?>, Type> entry : scanResult.entrySet()) {
            result.put(entry.getKey(), new TypeAndName(entry.getValue(), resolveClassName(entry.getKey(), scanResult)));
        }
        enforceNoNameCollisions(result);
        return result;
    }

    private void enforceNoNameCollisions(Map<Class<?>, TypeAndName> state) throws MojoExecutionException {
        final var clashingGroups = state.entrySet().stream()
                .collect(Collectors.groupingBy(entry -> entry.getValue().className().canonicalName()))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .toList();

        if (clashingGroups.isEmpty()) {
            return;
        }
        final var message = clashingGroups.stream().map(cg -> {
            final var className = cg.getKey();
            final var collisions = cg.getValue().stream().map(e -> e.getKey().getName()).collect(Collectors.joining(","));
            return "Class name collision target '%s' found in multiple source packages: %s".formatted(className, collisions);            
        }).collect(Collectors.joining("\n"));
        throw new MojoExecutionException("name collision while generating dtos: %s".formatted(message));
    }

    private ClassName resolveClassName(Class<?> clazz, Map<Class<?>, Type> allDiscovered) {
        final String name = getMappedName(clazz);
        final Class<?> declaring = clazz.getDeclaringClass();

        if (declaring != null && allDiscovered.containsKey(declaring)) {
            return resolveClassName(declaring, allDiscovered).nestedClass(name);
        }

        return ClassName.get(targetPackage, name);
    }

    private String getMappedName(Class<?> clazz) {
        final Downstream.Rename rename = clazz.getAnnotation(Downstream.Rename.class);
        return rename != null ? rename.value() : clazz.getSimpleName();
    }

}
