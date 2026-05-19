package net.optionfactory.spring.downstream.plugin;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.classgraph.ClassGraph;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.downstream.Downstream;

@Mojo(name = "generate-client", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class Plugin extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(required = true)
    private File scanDirectory;

    @Parameter(required = true)
    private String scanPackage;

    @Parameter(required = true)
    private String client;

    @Parameter(required = true)
    private String targetPackage;

    public record State(File outputDir, Set<AnnotatedMethod> methods, Set<Class<?>> dtos, Set<Class<?>> enums) {

        public static State of(File outputDir) {
            return new State(outputDir, new HashSet<>(), new HashSet<>(), new HashSet<>());
        }
    }

    public record AnnotatedMethod(Method method, Downstream.Method annotation) {

    }

    @Override
    public void execute() throws MojoExecutionException {
        final var state = State.of(new File(project.getBuild().getDirectory(), "generated-sources/downstream"));

        try {
            final var urls = new URL[]{scanDirectory.toURI().toURL()};
            try (final var classLoader = new URLClassLoader(urls, this.getClass().getClassLoader())) {

                try (final var scanResult = new ClassGraph()
                        .overrideClassLoaders(classLoader)
                        .enableMethodInfo()
                        .enableAnnotationInfo()
                        .scan()) {

                    for (final var classInfo : scanResult.getClassesWithMethodAnnotation(Downstream.Method.class.getName())) {
                        for (final var method : classInfo.loadClass().getDeclaredMethods()) {
                            final var annotation = method.getAnnotation(Downstream.Method.class);
                            if (annotation != null) {
                                state.methods().add(new AnnotatedMethod(method, annotation));
                            }
                        }
                    }
                }

                discoverAndRegisterDtos(state);
                enforceNoNameCollisions(state);

                for (final var dtoClass : state.dtos()) {
                    generateDto(state, dtoClass);
                }

                for (final var enumClass : state.enums()) {
                    generateEnum(state, enumClass);
                }
            }
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Downstream code generation failed", e);
        }

        project.addCompileSourceRoot(state.outputDir().getAbsolutePath());
        getLog().info("Appended generated sources configuration to compilation pipeline: " + state.outputDir().getAbsolutePath());
    }

    private void discoverAndRegisterDtos(State state) {
        final var ams = state.methods().stream().filter(m
                -> Stream.of(m.annotation().value()).anyMatch(c -> c.equals(client) || c.equals(""))
        ).toList();

        for (final var am : ams) {
            registerIfDto(state, am.method().getAnnotatedReturnType());
            for (final var param : am.method().getParameters()) {
                registerIfDto(state, param.getAnnotatedType());
            }
        }
    }

    private void enforceNoNameCollisions(State state) throws MojoExecutionException {
        final var allDiscoveredTypes = Stream.concat(state.dtos().stream(), state.enums().stream()).toList();
        final var clashingGroups = allDiscoveredTypes.stream()
                .collect(Collectors.groupingBy(Class::getSimpleName))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .toList();

        if (clashingGroups.isEmpty()) {
            return;
        }
        for (final var clashingGroup : clashingGroups) {
            final var className = clashingGroup.getKey();
            final var collisions = clashingGroup.getValue().stream().map(Class::getName).collect(Collectors.joining(","));
            getLog().warn(String.format("Class name collision target '%s' found in multiple source packages: %s", className, collisions));
        }
        throw new MojoExecutionException("name collision while generating dtos");
    }

    private void generateDto(State state, Class<?> dtoClass) throws IOException {
        if (existsInSourceTree(dtoClass.getSimpleName())) {
            getLog().info("Skipping DTO generation (already exists in src/main/java): " + dtoClass.getSimpleName());
            return;
        }
        getLog().info("Mapping Immutable Client Record: " + dtoClass.getSimpleName());

        final var recordBuilder = TypeSpec.recordBuilder(dtoClass.getSimpleName())
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Generated from {@code $L}.\nDO NOT EDIT MANUALLY.\n", dtoClass.getName());

        for (final var field : dtoClass.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }

            if (field.isAnnotationPresent(Downstream.Ignore.class)) {
                continue;
            }

            final var fieldType = mapType(field.getAnnotatedType());
            recordBuilder.addField(fieldType, field.getName());
        }
        JavaFile.builder(targetPackage, recordBuilder.build()).build().writeTo(state.outputDir());
    }

    private void generateEnum(State state, Class<?> enumClass) throws IOException {
        if (existsInSourceTree(enumClass.getSimpleName())) {
            getLog().info("Skipping Enum generation (already exists in src/main/java): " + enumClass.getSimpleName());
            return;
        }

        getLog().info("Mapping Client Enum: " + enumClass.getSimpleName());

        final var enumBuilder = TypeSpec.enumBuilder(enumClass.getSimpleName())
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Generated from {@code $L}: do not edit.", enumClass.getName());

        for (final var constant : enumClass.getEnumConstants()) {
            enumBuilder.addEnumConstant(((Enum<?>) constant).name());
        }

        JavaFile.builder(targetPackage, enumBuilder.build()).build().writeTo(state.outputDir());
    }

    private boolean existsInSourceTree(String className) {
        final var relativePath = "src/main/java/" + targetPackage.replace('.', '/') + "/" + className + ".java";
        final var sourceFile = new File(project.getBasedir(), relativePath);
        return sourceFile.exists();
    }

    private void registerIfDto(State state, AnnotatedType annotatedType) {
        if (annotatedType == null || annotatedType.isAnnotationPresent(Downstream.Ignore.class)) {
            return;
        }

        if (annotatedType instanceof AnnotatedParameterizedType apt) {
            for (final var arg : apt.getAnnotatedActualTypeArguments()) {
                registerIfDto(state, arg);
            }
            if (apt.getType() instanceof java.lang.reflect.ParameterizedType pType && pType.getRawType() instanceof Class<?> clazz) {
                processClassIfDto(state, clazz);
            }
        } else if (annotatedType.getType() instanceof Class<?> clazz) {
            var elementClass = clazz;
            while (elementClass.isArray()) {
                elementClass = elementClass.getComponentType();
            }
            processClassIfDto(state, elementClass);
        }
    }

    private void processClassIfDto(State state, Class<?> clazz) {
        if (clazz.getName().startsWith(scanPackage) && !clazz.isAnnotation()) {
            if (clazz.isEnum()) {
                state.enums().add(clazz);
            } else {
                if (state.dtos().add(clazz)) {
                    for (final var field : clazz.getDeclaredFields()) {
                        if (!field.isSynthetic()) {
                            registerIfDto(state, field.getAnnotatedType());
                        }
                    }
                }
            }
        }
    }

    private TypeName mapType(AnnotatedType annotatedType) {
        if (annotatedType == null || annotatedType.isAnnotationPresent(Downstream.Ignore.class)) {
            return ClassName.OBJECT;
        }

        final var type = annotatedType.getType();

        if (annotatedType instanceof AnnotatedParameterizedType apt && type instanceof java.lang.reflect.ParameterizedType pType) {
            final var rawType = TypeName.get(pType.getRawType());
            final var typeArgs = Arrays.stream(apt.getAnnotatedActualTypeArguments())
                    .map(this::mapType)
                    .toArray(TypeName[]::new);
            if (rawType instanceof ClassName className) {
                return ParameterizedTypeName.get(className, typeArgs);
            }
        }

        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                var elementClass = clazz;
                var dimensions = 0;
                while (elementClass.isArray()) {
                    elementClass = elementClass.getComponentType();
                    dimensions++;
                }

                TypeName componentType = elementClass.getName().startsWith(scanPackage)
                        ? ClassName.get(targetPackage, elementClass.getSimpleName())
                        : TypeName.get(elementClass);

                for (int i = 0; i < dimensions; i++) {
                    componentType = ArrayTypeName.of(componentType);
                }
                return componentType;
            }

            if (clazz.getName().startsWith(scanPackage)) {
                return ClassName.get(targetPackage, clazz.getSimpleName());
            }
            return TypeName.get(clazz);
        }
        return TypeName.get(type);
    }
}
