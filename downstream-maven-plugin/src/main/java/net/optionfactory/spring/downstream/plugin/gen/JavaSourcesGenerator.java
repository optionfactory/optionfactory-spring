package net.optionfactory.spring.downstream.plugin.gen;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
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
import net.optionfactory.spring.downstream.plugin.processing.TypesRegistry;
import net.optionfactory.spring.downstream.plugin.processing.TypesTranslator;

public class JavaSourcesGenerator implements SourcesGenerator {

    private final File outputDir;
    private final File projectBaseDir;
    private final String targetPackage;
    private final Map<String, String> translations;
    private final JavaOutputStyle javaOutputStyle;

    public enum JavaOutputStyle {
        CLASSES, RECORDS;
    }

    public JavaSourcesGenerator(File outputDir, File projectBaseDir, String targetPackage, Map<String, String> translations, JavaOutputStyle javaOutputStyle) {
        this.outputDir = outputDir;
        this.projectBaseDir = projectBaseDir;
        this.targetPackage = targetPackage;
        this.translations = translations;
        this.javaOutputStyle = javaOutputStyle;
    }

    @Override
    public List<GenerateOutcome> generate(TypesRegistry types, SourcesClassLoader cl) throws IOException {
        final var outcomes = new ArrayList<GenerateOutcome>();
        final var translator = new TypesTranslator(types, translations);
        for (final var mapping : types.allMappings()) {
            final var clazz = mapping.getKey();
            if (!types.isRoot(clazz)) {
                continue;
            }
            final var rootSpec = buildSpec(cl, types, translator, types.getType(clazz), clazz, true);
            outcomes.add(writeRootFile(outputDir, mapping.getValue().className(), rootSpec));
        }
        return outcomes;
    }

    private TypeSpec buildSpec(SourcesClassLoader cl, TypesRegistry types, TypesTranslator translator, PayloadType type, Class<?> clazz, boolean root) {
        return type == PayloadType.DTO ? buildDtoSpec(cl, types, translator, clazz, root) : buildEnumSpec(cl, types, translator, clazz, root);
    }

    private TypeSpec buildDtoSpec(SourcesClassLoader cl, TypesRegistry types, TypesTranslator translator, Class<?> dtoClass, boolean root) {
        final var mappedName = types.getClassName(dtoClass);
        final var typeBuilder = (javaOutputStyle == JavaOutputStyle.CLASSES ? TypeSpec.classBuilder(mappedName.simpleName()) : TypeSpec.recordBuilder(mappedName.simpleName()))
                .addModifiers(Modifier.PUBLIC);

        for (final var typeParam : dtoClass.getTypeParameters()) {
            final var translatedBounds = Arrays.stream(typeParam.getAnnotatedBounds())
                    .map(b -> translator.translate(b, cl))
                    .filter(bound -> !bound.equals(ClassName.OBJECT))
                    .toArray(TypeName[]::new);
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParam.getName(), translatedBounds));
        }

        if (root) {
            typeBuilder.addJavadoc("Generated from {@code $L}", dtoClass.getName());
        }

        final var declaring = dtoClass.getDeclaringClass();
        if (declaring != null && types.isRegistered(declaring)) {
            typeBuilder.addModifiers(Modifier.STATIC);
        }
        final var fieldHierarchy = new ArrayList<Field>();
        Class<?> current = dtoClass;
        while (current != null && current != Object.class) {
            fieldHierarchy.addAll(0, Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        if (javaOutputStyle == JavaOutputStyle.CLASSES) {
            for (final var field : fieldHierarchy) {
                if (field.isSynthetic() || field.isAnnotationPresent(Downstream.Ignore.class)) {
                    continue;
                }
                final var fieldType = translator.translate(field.getAnnotatedType(), cl);
                typeBuilder.addField(FieldSpec.builder(fieldType, field.getName(), Modifier.PUBLIC).build());
            }
        } else {
            final var constructorBuilder = MethodSpec.constructorBuilder();

            for (final var field : fieldHierarchy) {
                if (field.isSynthetic() || field.isAnnotationPresent(Downstream.Ignore.class)) {
                    continue;
                }
                final var fieldType = translator.translate(field.getAnnotatedType(), cl);
                constructorBuilder.addParameter(fieldType, field.getName());
            }
            typeBuilder.recordConstructor(constructorBuilder.build());
        }

        for (final var nested : dtoClass.getDeclaredClasses()) {
            if (!types.isRegistered(nested)) {
                continue;
            }
            typeBuilder.addType(buildSpec(cl, types, translator, types.getType(nested), nested, false));
        }
        return typeBuilder.build();
    }

    private TypeSpec buildEnumSpec(SourcesClassLoader cl, TypesRegistry types, TypesTranslator translator, Class<?> enumClass, boolean root) {
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
            enumBuilder.addType(buildSpec(cl, types, translator, types.getType(nested), nested, false));
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
