package net.optionfactory.spring.downstream.plugin.processing;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Map;
import net.optionfactory.spring.downstream.Downstream;
import net.optionfactory.spring.downstream.plugin.gen.SourcesGenerator.SourcesClassLoader;

public class TypesTranslator {

    private final TypesRegistry types;
    private final Map<String, String> translations;

    public TypesTranslator(TypesRegistry types, Map<String, String> translations) {
        this.types = types;
        this.translations = translations;
    }

    public TypeName translate(AnnotatedType annotatedType, SourcesClassLoader cl) {
        if (annotatedType == null || annotatedType.isAnnotationPresent(Downstream.Ignore.class)) {
            return ClassName.OBJECT;
        }

        final var type = annotatedType.getType();

        if (annotatedType instanceof AnnotatedParameterizedType apt && type instanceof ParameterizedType pType) {
            final var typeArgs = Arrays.stream(apt.getAnnotatedActualTypeArguments())
                    .map(at -> translate(at, cl))
                    .toArray(TypeName[]::new);

            if (pType.getRawType() instanceof Class<?> rawClass && translations.containsKey(rawClass.getName())) {
                TypeName substitutedRaw = resolveTarget(translations.get(rawClass.getName()));
                if (substitutedRaw instanceof ClassName className && typeArgs.length > 0 && cl.load(className.reflectionName()).getTypeParameters().length > 0) {
                    return ParameterizedTypeName.get(className, typeArgs);
                }
                return substitutedRaw;
            }

            final var rawType = TypeName.get(pType.getRawType());
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
                if (translations.containsKey(elementClass.getName())) {
                    componentType = resolveTarget(translations.get(elementClass.getName()));
                } else if (types.isRegistered(elementClass)) {
                    componentType = types.getClassName(elementClass);
                } else {
                    componentType = TypeName.get(elementClass);
                }

                for (int i = 0; i < dimensions; i++) {
                    componentType = ArrayTypeName.of(componentType);
                }
                return componentType;
            }

            if (translations.containsKey(clazz.getName())) {
                return resolveTarget(translations.get(clazz.getName()));
            }

            if (types.isRegistered(clazz)) {
                return types.getClassName(clazz);
            }
            return TypeName.get(clazz);
        }

        return TypeName.get(type);
    }

    private TypeName resolveTarget(String target) {
        if (target.endsWith("[]")) {
            return ArrayTypeName.of(resolveTarget(target.substring(0, target.length() - 2)));
        }
        return switch (target) {
            case "byte" ->
                TypeName.BYTE;
            case "short" ->
                TypeName.SHORT;
            case "int" ->
                TypeName.INT;
            case "long" ->
                TypeName.LONG;
            case "float" ->
                TypeName.FLOAT;
            case "double" ->
                TypeName.DOUBLE;
            case "boolean" ->
                TypeName.BOOLEAN;
            case "char" ->
                TypeName.CHAR;
            case "void" ->
                TypeName.VOID;
            default ->
                ClassName.bestGuess(target);
        };
    }
}
