package net.optionfactory.spring.downstream.plugin.emit.java;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import net.optionfactory.spring.downstream.plugin.emit.SourceEmitter;
import net.optionfactory.spring.downstream.plugin.emit.SourceEmitter.GenerateOutcome;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.TargetName;
import net.optionfactory.spring.downstream.plugin.reflection.Reflection;

public class JavaEmitter implements SourceEmitter {

    private final File outputDir;
    private final File projectBaseDir;
    private final Map<String, String> translations;
    private final DtoStyle dtoStyle;
    private static final ClassName NULLABLE = ClassName.get("org.jspecify.annotations", "Nullable");
    private static final ClassName NONNULL = ClassName.get("org.jspecify.annotations", "NonNull");

    public enum DtoStyle {
        RECORDS, CLASSES;
    }

    public JavaEmitter(File outputDir, File projectBaseDir, Map<String, String> translations, DtoStyle dtoStyle) {
        this.outputDir = outputDir;
        this.projectBaseDir = projectBaseDir;
        this.translations = translations;
        this.dtoStyle = dtoStyle;
    }

    @Override
    public List<GenerateOutcome> emit(TypeRegistry registry) throws Exception {
        final var outcomes = new ArrayList<GenerateOutcome>();
        final var translator = new JavaTypeTranslator(registry, translations);

        for (final var sourceClass : registry.allSourceClasses()) {
            final var target = registry.getTargetName(sourceClass);
            if (target.names().size() > 1) {
                continue;
            }
            final var rootSpec = buildSpec(sourceClass, registry, translator, true);
            final var relativePath = "src/main/java/%s/%s.java".formatted(target.packageName().replace('.', '/'), target.topLevelName());
            final var sourceFile = new File(projectBaseDir, relativePath);
            if (sourceFile.exists()) {
                outcomes.add(new GenerateOutcome(relativePath, false));
                continue;
            }
            JavaFile.builder(target.packageName(), rootSpec)
                    .skipJavaLangImports(true)
                    .build()
                    .writeTo(outputDir);
            outcomes.add(new GenerateOutcome(relativePath, true));
        }

        return outcomes;
    }

    private TypeSpec buildSpec(Class<?> clazz, TypeRegistry registry, JavaTypeTranslator translator, boolean root) {
        return clazz.isEnum()
                ? buildEnumSpec(clazz, registry, translator, root)
                : buildDtoSpec(clazz, registry, translator, root);
    }

    private TypeSpec buildDtoSpec(Class<?> dtoClass, TypeRegistry registry, JavaTypeTranslator translator, boolean root) {
        final var targetName = registry.getTargetName(dtoClass);
        final var simpleName = targetName.names().getLast();

        final var typeBuilder = (DtoStyle.CLASSES == dtoStyle
                ? TypeSpec.classBuilder(simpleName)
                : TypeSpec.recordBuilder(simpleName))
                .addModifiers(Modifier.PUBLIC);

        for (final var typeParam : dtoClass.getTypeParameters()) {
            final var translatedBounds = Arrays.stream(typeParam.getAnnotatedBounds())
                    .map(translator::translate)
                    .filter(bound -> !bound.equals(ClassName.OBJECT))
                    .toArray(TypeName[]::new);
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParam.getName(), translatedBounds));
        }

        if (root) {
            typeBuilder.addJavadoc("Mapped from {@code $L}", dtoClass.getName());
        } else {
            typeBuilder.addModifiers(Modifier.STATIC);
        }

        final var fields = Reflection.candidateFields(dtoClass, Object.class);

        if (dtoStyle == DtoStyle.CLASSES) {
            for (final var field : fields) {
                final var fieldType = translator.translate(field.annotatedType());
                final var fieldSpecBuilder = FieldSpec.builder(fieldType, field.name(), Modifier.PUBLIC);
                if (field.nullable()) {
                    fieldSpecBuilder.addAnnotation(NULLABLE);
                }
                if (field.nonNull()) {
                    fieldSpecBuilder.addAnnotation(NONNULL);
                }
                typeBuilder.addField(fieldSpecBuilder.build());
            }
        } else {
            final var constructorBuilder = MethodSpec.constructorBuilder();
            for (final var field : fields) {
                final var fieldType = translator.translate(field.annotatedType());
                final var paramSpecBuilder = ParameterSpec.builder(fieldType, field.name());
                if (field.nullable()) {
                    paramSpecBuilder.addAnnotation(NULLABLE);
                }
                if (field.nonNull()) {
                    paramSpecBuilder.addAnnotation(NONNULL);
                }
                constructorBuilder.addParameter(paramSpecBuilder.build());
            }
            typeBuilder.recordConstructor(constructorBuilder.build());
        }

        for (final var nested : dtoClass.getDeclaredClasses()) {
            if (registry.isRegistered(nested)) {
                final TargetName nestedTarget = registry.getTargetName(nested);
                // only embed the code inside the parent if the registry says it's NESTED
                if (nestedTarget.names().size() > 1) {
                    typeBuilder.addType(buildSpec(nested, registry, translator, false));
                }
            }
        }
        return typeBuilder.build();
    }

    private TypeSpec buildEnumSpec(Class<?> enumClass, TypeRegistry registry, JavaTypeTranslator translator, boolean root) {
        final var targetName = registry.getTargetName(enumClass);
        final var simpleName = targetName.names().get(targetName.names().size() - 1);

        final var enumBuilder = TypeSpec.enumBuilder(simpleName).addModifiers(Modifier.PUBLIC);

        if (root) {
            enumBuilder.addJavadoc("Mapped from {@code $L}", enumClass.getName());
        } else {
            enumBuilder.addModifiers(Modifier.STATIC);
        }

        for (final var constant : enumClass.getEnumConstants()) {
            enumBuilder.addEnumConstant(((Enum<?>) constant).name());
        }

        for (final var nested : enumClass.getDeclaredClasses()) {
            if (registry.isRegistered(nested)) {
                final TargetName nestedTarget = registry.getTargetName(nested);
                if (nestedTarget.names().size() > 1) {
                    enumBuilder.addType(buildSpec(nested, registry, translator, false));
                }
            }
        }
        return enumBuilder.build();
    }

}
