package net.optionfactory.spring.downstream.plugin.emit.java;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Map;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.TargetName;

public class JavaTypeTranslator {

    private final TypeRegistry registry;
    private final Map<String, String> translations;

    public JavaTypeTranslator(TypeRegistry registry, Map<String, String> translations) {
        this.registry = registry;
        this.translations = translations;
    }

    public TypeName translate(AnnotatedType annotatedType) {
        final var type = annotatedType.getType();
        if (annotatedType instanceof AnnotatedParameterizedType apt && type instanceof ParameterizedType pType) {
            final var typeArgs = Arrays.stream(apt.getAnnotatedActualTypeArguments()).map(this::translate).toArray(TypeName[]::new);
            if (pType.getRawType() instanceof Class<?> rawClass && translations.containsKey(rawClass.getName())) {
                TypeName substitutedRaw = resolveTarget(translations.get(rawClass.getName()));
                if (substitutedRaw instanceof ClassName className && typeArgs.length > 0) {
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
                Class<?> elementClass = clazz;
                int dimensions = 0;
                while (elementClass.isArray()) {
                    elementClass = elementClass.getComponentType();
                    dimensions++;
                }
                TypeName componentType;
                if (translations.containsKey(elementClass.getName())) {
                    componentType = resolveTarget(translations.get(elementClass.getName()));
                } else if (registry.isRegistered(elementClass)) {
                    componentType = toClassName(registry.getTargetName(elementClass));
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
            if (registry.isRegistered(clazz)) {
                return toClassName(registry.getTargetName(clazz));
            }
            return TypeName.get(clazz);
        }
        return TypeName.get(type);
    }

    private ClassName toClassName(TargetName targetName) {
        if (targetName.names().size() == 1) {
            return ClassName.get(targetName.packageName(), targetName.names().get(0));
        }
        final var topLevel = targetName.names().get(0);
        final var inners = targetName.names().subList(1, targetName.names().size()).toArray(String[]::new);
        return ClassName.get(targetName.packageName(), topLevel, inners);
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
