package net.optionfactory.spring.downstream.plugin.processing;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import net.optionfactory.spring.downstream.Downstream;
import net.optionfactory.spring.downstream.plugin.processing.PayloadsScanner.PayloadType;

public class SourcesGenerator {

    private final File outputDir;
    private final File projectBaseDir;
    private final String targetPackage;
    private final Map<String, String> translations;

    public SourcesGenerator(File outputDir, File projectBaseDir, String targetPackage, Map<String, String> translations) {
        this.outputDir = outputDir;
        this.projectBaseDir = projectBaseDir;
        this.targetPackage = targetPackage;
        this.translations = translations;
    }

    public record GenerateOutcome(String name, boolean generated) {

    }

    public List<GenerateOutcome> generate(TypesRegistry types) throws IOException {
        final var outcomes = new ArrayList<GenerateOutcome>();
        final var translator = new TypesTranslator(types, translations);
        for (final var mapping : types.allMappings()) {
            final var clazz = mapping.getKey();
            if (!types.isRoot(clazz)) {
                continue;
            }
            final var rootSpec = buildSpec(types, translator, types.getType(clazz), clazz, true);
            outcomes.add(writeRootFile(outputDir, mapping.getValue().className(), rootSpec));
        }
        return outcomes;
    }

    private TypeSpec buildSpec(TypesRegistry types, TypesTranslator translator, PayloadType type, Class<?> clazz, boolean root) {
        return type == PayloadType.DTO ? buildDtoSpec(types, translator, clazz, root) : buildEnumSpec(types, translator, clazz, root);
    }

    private TypeSpec buildDtoSpec(TypesRegistry types, TypesTranslator translator, Class<?> dtoClass, boolean root) {
        final var mappedName = types.getClassName(dtoClass);
        final var recordBuilder = TypeSpec.recordBuilder(mappedName.simpleName())
                .addModifiers(Modifier.PUBLIC);

        for (final var typeParam : dtoClass.getTypeParameters()) {
            final var translatedBounds = Arrays.stream(typeParam.getAnnotatedBounds())
                    .map(translator::translate)
                    .filter(bound -> !bound.equals(ClassName.OBJECT)) // Strips default implicit Object bounds
                    .toArray(TypeName[]::new);
            recordBuilder.addTypeVariable(TypeVariableName.get(typeParam.getName(), translatedBounds));
        }

        if (root) {
            recordBuilder.addJavadoc("Generated from {@code $L}", dtoClass.getName());
        }
        final var declaring = dtoClass.getDeclaringClass();
        if (declaring != null && types.isRegistered(declaring)) {
            recordBuilder.addModifiers(Modifier.STATIC);
        }

        final var constructorBuilder = MethodSpec.constructorBuilder();

        final var fieldHierarchy = new ArrayList<Field>();
        Class<?> current = dtoClass;
        while (current != null && current != Object.class) {
            fieldHierarchy.addAll(0, Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }

        for (final var field : fieldHierarchy) {
            if (field.isSynthetic() || field.isAnnotationPresent(Downstream.Ignore.class)) {
                continue;
            }
            final var fieldType = translator.translate(field.getAnnotatedType());
            constructorBuilder.addParameter(fieldType, field.getName());
        }
        recordBuilder.recordConstructor(constructorBuilder.build());

        for (final var nested : dtoClass.getDeclaredClasses()) {
            if (!types.isRegistered(nested)) {
                continue;
            }
            recordBuilder.addType(buildSpec(types, translator, types.getType(nested), nested, false));
        }
        return recordBuilder.build();
    }

    private TypeSpec buildEnumSpec(TypesRegistry types, TypesTranslator translator, Class<?> enumClass, boolean root) {
        final ClassName mappedName = types.getClassName(enumClass);

        final var enumBuilder = TypeSpec.enumBuilder(mappedName.simpleName())
                .addModifiers(Modifier.PUBLIC);
        if (root) {
            enumBuilder.addJavadoc("Generated from {@code $L}", enumClass.getName());
        }
        final var declaring = enumClass.getDeclaringClass();
        if (declaring != null && types.isRegistered(declaring)) {
            enumBuilder.addModifiers(Modifier.STATIC);
        }
        for (final var constant : enumClass.getEnumConstants()) {
            enumBuilder.addEnumConstant(((Enum<?>) constant).name());
        }
        for (final var nested : enumClass.getDeclaredClasses()) {
            if (!types.isRegistered(nested)) {
                continue;
            }
            enumBuilder.addType(buildSpec(types, translator, types.getType(nested), nested, false));
        }
        return enumBuilder.build();
    }

    private GenerateOutcome writeRootFile(File outputDir, ClassName rootClassName, TypeSpec rootSpec) throws IOException {
        final var relativePath = "src/main/java/%s/%s.java".formatted(targetPackage.replace('.', '/'), rootClassName.simpleNames().get(0));
        final var sourceFile = new File(projectBaseDir, relativePath);
        if (sourceFile.exists()) {
            return new GenerateOutcome(relativePath, false);
        }
        JavaFile.builder(targetPackage, rootSpec)
                .skipJavaLangImports(true)
                .build()
                .writeTo(outputDir);
        return new GenerateOutcome(relativePath, true);
    }
}