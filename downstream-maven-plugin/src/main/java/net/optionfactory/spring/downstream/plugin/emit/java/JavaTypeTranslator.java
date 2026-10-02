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

/// Maps the java type of a source property to the type of the generated property.
///
/// In order of precedence a class is replaced by:
/// 1. its translation, when it has one: the generated counterpart of the target when the target
///    is a payload, otherwise the target itself, which can be a class (binary `Outer$Inner` names
///    accepted), a primitive or an array of them (`byte[]`);
/// 2. its generated counterpart, when it is a payload;
/// 3. itself.
///
/// Arrays are mapped component by component, and parameterized types argument by argument. The
/// raw class of a parameterized type is mapped as any other class (a `Page<User>` property where
/// `Page` is a payload is generated as the generated `Page` of the generated `User`), and the
/// mapped arguments are kept unless it maps to a primitive or an array.
public class JavaTypeTranslator {

    private final TypeRegistry registry;
    private final Map<String, String> translations;

    /// @param registry the payload types and their generated names
    /// @param translations source class binary name to replacement type
    public JavaTypeTranslator(TypeRegistry registry, Map<String, String> translations) {
        this.registry = registry;
        this.translations = translations;
    }

    /// @param annotatedType the type of a source property
    /// @return the type of the generated property
    public TypeName translate(AnnotatedType annotatedType) {
        final var type = annotatedType.getType();
        if (annotatedType instanceof AnnotatedParameterizedType apt && type instanceof ParameterizedType pType) {
            final var typeArgs = Arrays.stream(apt.getAnnotatedActualTypeArguments()).map(this::translate).toArray(TypeName[]::new);
            if (pType.getRawType() instanceof Class<?> rawClass) {
                final var rawType = resolveClassType(rawClass);
                if (rawType instanceof ClassName className && typeArgs.length > 0) {
                    return ParameterizedTypeName.get(className, typeArgs);
                }
                return rawType;
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
                var componentType = resolveClassType(elementClass);
                for (int i = 0; i < dimensions; i++) {
                    componentType = ArrayTypeName.of(componentType);
                }
                return componentType;
            }
            return resolveClassType(clazz);
        }
        return TypeName.get(type);
    }

    private TypeName resolveClassType(Class<?> clazz) {
        final var originalFqn = clazz.getName();
        if (translations.containsKey(originalFqn)) {
            final String translatedFqn = translations.get(originalFqn);
            if (registry.isRegistered(translatedFqn)) {
                return toClassName(registry.getTargetName(translatedFqn));
            }
            return resolveTarget(translatedFqn.replace('$', '.'));
        }
        if (registry.isRegistered(clazz)) {
            return toClassName(registry.getTargetName(clazz));
        }
        return TypeName.get(clazz);
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
            case "byte" -> TypeName.BYTE;
            case "short" -> TypeName.SHORT;
            case "int" -> TypeName.INT;
            case "long" -> TypeName.LONG;
            case "float" -> TypeName.FLOAT;
            case "double" -> TypeName.DOUBLE;
            case "boolean" -> TypeName.BOOLEAN;
            case "char" -> TypeName.CHAR;
            case "void" -> TypeName.VOID;
            default -> ClassName.bestGuess(target);
        };
    }
}