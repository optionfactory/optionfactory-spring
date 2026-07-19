package net.optionfactory.spring.downstream.plugin.emit.ts;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;

public class TypeScriptTypeTranslator {

    private final TypeRegistry registry;
    private final Map<String, String> translations;
    private final Map<String, String> typeAliases;

    public static final Set<String> VALID_RECORD_KEY_TYPES = Set.of("string", "number");
    public static final Set<String> TS_PRIMITIVES = Set.of("string", "number", "boolean", "any", "void");

    public TypeScriptTypeTranslator(TypeRegistry registry, Map<String, String> translations, Map<String, String> typeAliases) {
        this.registry = registry;
        this.translations = translations;
        this.typeAliases = typeAliases;
    }

    public String translate(Type type) {
        if (type instanceof GenericArrayType gat) {
            return translate(gat.getGenericComponentType()) + "[]";
        }
        if (type instanceof ParameterizedType pt) {
            final Type rawType = pt.getRawType();
            if (rawType instanceof Class<?> rawClass) {
                final String rawFqn = rawClass.getName();
                if (translations.containsKey(rawFqn) || typeAliases.containsKey(rawFqn)) {
                    final String translatedRaw = translate(rawClass);
                    if (TS_PRIMITIVES.contains(translatedRaw)) {
                        return translatedRaw;
                    }
                    final var typeArgs = Arrays.stream(pt.getActualTypeArguments())
                            .map(this::translate)
                            .collect(Collectors.joining(", "));
                    return "%s<%s>".formatted(translatedRaw, typeArgs);
                }

                if (Optional.class.isAssignableFrom(rawClass)) {
                    return translate(pt.getActualTypeArguments()[0]);
                }
                if (Collection.class.isAssignableFrom(rawClass)) {
                    return "%s[]".formatted(translate(pt.getActualTypeArguments()[0]));
                }
                if (Map.class.isAssignableFrom(rawClass)) {
                    final var sourceKeyType = pt.getActualTypeArguments()[0];
                    final var keyType = translate(sourceKeyType);
                    final var valType = translate(pt.getActualTypeArguments()[1]);
                    final var isEnum = sourceKeyType instanceof Class<?> keyClass && keyClass.isEnum();
                    final var actualKeyType = VALID_RECORD_KEY_TYPES.contains(keyType) || isAlias(keyType) || isEnum
                            ? keyType
                            : "string";
                    return "Record<%s, %s>".formatted(actualKeyType, valType);
                }
                final var typeArgs = Arrays.stream(pt.getActualTypeArguments())
                        .map(this::translate)
                        .collect(Collectors.joining(", "));
                return "%s<%s>".formatted(translate(rawClass), typeArgs);
            }
        }

        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                return "%s[]".formatted(translate(clazz.getComponentType()));
            }
            final var originalFqn = clazz.getName();
            if (typeAliases.containsKey(originalFqn)) {
                return simpleName(originalFqn);
            }

            final var fqn = translations.getOrDefault(originalFqn, originalFqn);

            if (!fqn.equals(originalFqn) && typeAliases.containsKey(fqn)) {
                return simpleName(originalFqn);                
            }

            if (!fqn.equals(originalFqn) && registry.isRegistered(fqn)) {
                return registry.getTargetName(fqn).flatName();
            }

            if (fqn.equals(originalFqn) && (registry.isRegistered(clazz) || clazz.isEnum())) {
                return registry.isRegistered(clazz)
                        ? registry.getTargetName(clazz).flatName()
                        : clazz.getSimpleName();
            }
            return switch (fqn) {
                case "java.lang.String", "char", "java.lang.Character" ->
                    "string";
                case "int", "long", "double", "float", "short", "byte", "java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.lang.Float", "java.lang.Short", "java.lang.Byte", "java.math.BigDecimal", "java.math.BigInteger" ->
                    "number";
                case "boolean", "java.lang.Boolean" ->
                    "boolean";
                case "java.lang.Object", "java.util.Optional" ->
                    "any";
                case "void", "java.lang.Void" ->
                    "void";
                default ->
                    fqn.equals(originalFqn) ? "any" : simpleName(fqn);
            };
        }
        if (type instanceof TypeVariable<?> tv) {
            return tv.getName();
        }
        if (type instanceof WildcardType wt) {
            if (wt.getUpperBounds().length > 0 && wt.getUpperBounds()[0] != Object.class) {
                return translate(wt.getUpperBounds()[0]);
            }
            return "any";
        }
        return "any";
    }

    public static String simpleName(String fqn) {
        if (fqn == null) {
            return null;
        }
        return fqn.substring(Math.max(fqn.lastIndexOf('.'), fqn.lastIndexOf('$')) + 1);
    }

    private boolean isAlias(String typeName) {
        return typeAliases.keySet().stream()
                .map(TypeScriptTypeTranslator::simpleName)
                .anyMatch(aliasSimpleName -> aliasSimpleName.equals(typeName));
    }
}
