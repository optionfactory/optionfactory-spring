package net.optionfactory.spring.downstream.plugin.processing;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import net.optionfactory.spring.downstream.Downstream;
import net.optionfactory.spring.downstream.plugin.processing.DownstreamMappingsResolver.Type;
import net.optionfactory.spring.downstream.plugin.processing.DownstreamMappingsResolver.TypeAndName;

public class DownstreamSourcesGenerator {

    private final File outputDir;
    private final File projectBaseDir;
    private final String targetPackage;

    public DownstreamSourcesGenerator(File outputDir, File projectBaseDir, String targetPackage) {
        this.outputDir = outputDir;
        this.projectBaseDir = projectBaseDir;
        this.targetPackage = targetPackage;
    }

    public record GenerateCandidate(String name, boolean generated) {

    }

    public List<GenerateCandidate> generate(Map<Class<?>, TypeAndName> resolved) throws IOException {
        List<GenerateCandidate> report = new ArrayList<>();
        for (final var classToMapping : resolved.entrySet()) {
            final Class<?> declaring = classToMapping.getKey().getDeclaringClass();
            if (declaring == null || !resolved.containsKey(declaring)) {
                final TypeSpec rootSpec = classToMapping.getValue().type() == DownstreamMappingsResolver.Type.DTO
                        ? buildDtoSpec(resolved, classToMapping.getKey(), true)
                        : buildEnumSpec(resolved, classToMapping.getKey(), true);

                report.add(writeRootFile(outputDir, classToMapping.getValue().className(), rootSpec));
            }
        }
        return report;
    }

    private TypeSpec buildDtoSpec(Map<Class<?>, TypeAndName> result, Class<?> dtoClass, boolean root) {
        final ClassName mappedName = result.get(dtoClass).className();

        final var recordBuilder = TypeSpec.recordBuilder(mappedName.simpleName())
                .addModifiers(Modifier.PUBLIC);

        if (root) {
            recordBuilder.addJavadoc("Generated from {@code $L}", dtoClass.getName());
        }

        final Class<?> declaring = dtoClass.getDeclaringClass();
        if (declaring != null && result.containsKey(declaring)) {
            recordBuilder.addModifiers(Modifier.STATIC);
        }

        final var constructorBuilder = MethodSpec.constructorBuilder();

        for (final var field : dtoClass.getDeclaredFields()) {
            if (field.isSynthetic() || field.isAnnotationPresent(Downstream.Ignore.class)) {
                continue;
            }
            final var fieldType = mapType(result, field.getAnnotatedType());
            constructorBuilder.addParameter(fieldType, field.getName());
        }

        recordBuilder.recordConstructor(constructorBuilder.build());

        for (final Class<?> nested : dtoClass.getDeclaredClasses()) {
            final var n = result.get(nested);
            if (n == null) {
                continue;
            }
            recordBuilder.addType(n.type() == Type.DTO ? buildDtoSpec(result, nested, false) : buildEnumSpec(result, nested, false));
        }

        return recordBuilder.build();
    }

    private TypeSpec buildEnumSpec(Map<Class<?>, TypeAndName> state, Class<?> enumClass, boolean root) {
        final ClassName mappedName = state.get(enumClass).className();

        final var enumBuilder = TypeSpec.enumBuilder(mappedName.simpleName())
                .addModifiers(Modifier.PUBLIC);
        if (root) {
            enumBuilder.addJavadoc("Generated from {@code $L}", enumClass.getName());
        }

        final Class<?> declaring = enumClass.getDeclaringClass();
        if (declaring != null && state.containsKey(declaring)) {
            enumBuilder.addModifiers(Modifier.STATIC);
        }

        for (final var constant : enumClass.getEnumConstants()) {
            enumBuilder.addEnumConstant(((Enum<?>) constant).name());
        }

        for (final Class<?> nested : enumClass.getDeclaredClasses()) {
            final var n = state.get(nested);
            if (n == null) {
                continue;
            }
            enumBuilder.addType(n.type() == Type.DTO ? buildDtoSpec(state, nested, false) : buildEnumSpec(state, nested, false));
        }

        return enumBuilder.build();
    }

    private GenerateCandidate writeRootFile(File outputDir, ClassName rootClassName, TypeSpec rootSpec) throws IOException {
        final String topLevelName = rootClassName.simpleNames().get(0);

        final var relativePath = "src/main/java/" + targetPackage.replace('.', '/') + "/" + topLevelName + ".java";
        final var sourceFile = new File(projectBaseDir, relativePath);
        if (sourceFile.exists()) {
            return new GenerateCandidate(relativePath, false);
        }

        JavaFile.builder(targetPackage, rootSpec)
                .skipJavaLangImports(true)
                .build()
                .writeTo(outputDir);

        return new GenerateCandidate(relativePath, true);
    }

    private TypeName mapType(Map<Class<?>, TypeAndName> state, AnnotatedType annotatedType) {
        if (annotatedType == null || annotatedType.isAnnotationPresent(Downstream.Ignore.class)) {
            return ClassName.OBJECT;
        }

        final var type = annotatedType.getType();

        if (annotatedType instanceof AnnotatedParameterizedType apt && type instanceof java.lang.reflect.ParameterizedType pType) {
            final var rawType = TypeName.get(pType.getRawType());
            final var typeArgs = Arrays.stream(apt.getAnnotatedActualTypeArguments())
                    .map(arg -> mapType(state, arg))
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

                TypeName componentType;
                if (state.containsKey(elementClass)) {
                    componentType = state.get(elementClass).className();
                } else {
                    componentType = TypeName.get(elementClass);
                }

                for (int i = 0; i < dimensions; i++) {
                    componentType = ArrayTypeName.of(componentType);
                }
                return componentType;
            }
            if (state.containsKey(clazz)) {
                return state.get(clazz).className();
            }
            return TypeName.get(clazz);
        }

        return TypeName.get(type);
    }

}
