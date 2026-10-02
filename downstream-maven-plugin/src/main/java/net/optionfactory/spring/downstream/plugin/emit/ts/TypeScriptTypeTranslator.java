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

/// Maps the java type of a source property to a TypeScript type.
///
/// For a class, in order of precedence:
/// 1. an aliased class is referenced by its simple name, the alias being declared by the emitter;
/// 2. a translated class is replaced by the TypeScript type of its translation target: the
///    simple name of an aliased target, the flat name of a payload, the TypeScript type of a
///    well-known java type (primitives included), an array of the mapping of its component for
///    an array (`byte[]` becomes `number[]`), or else the target simple name;
/// 3. a payload is referenced by its [flat name][TypeRegistry.TargetName#flatName], and an enum
///    that is not a payload by its simple name;
/// 4. strings and `char` become `string`, numeric primitives, their wrappers, `BigDecimal` and
///    `BigInteger` become `number`, `boolean` becomes `boolean`, `void` becomes `void`;
/// 5. anything else, `Object` included, becomes `any`.
///
/// `Optional<T>` becomes `T` (the emitter marks the property optional), a `Collection<T>` or an
/// array becomes `T[]`, a `Map<K, V>` becomes `Record<K, V>` with `K` replaced by `string` unless
/// it maps to `string`, `number`, an enum or an alias. Other parameterized types keep their
/// arguments, but a generic class translated to a TypeScript primitive or to an array drops them.
/// Type variables keep their name and wildcards become their upper bound, or `any`.
public class TypeScriptTypeTranslator {

    private final TypeRegistry registry;
    private final Map<String, String> translations;
    private final Map<String, String> typeAliases;

    /// The TypeScript types kept as `Record` keys; other map keys, enums and aliases aside, become
    /// `string`.
    public static final Set<String> VALID_RECORD_KEY_TYPES = Set.of("string", "number");
    /// The TypeScript types that take no type arguments: a generic class translated to one of them
    /// drops its arguments.
    public static final Set<String> TS_PRIMITIVES = Set.of("string", "number", "boolean", "any", "void");

    /// @param registry the payload types and their generated names
    /// @param translations source class binary name to replacement java type
    /// @param typeAliases source class binary name to TypeScript type
    public TypeScriptTypeTranslator(TypeRegistry registry, Map<String, String> translations, Map<String, String> typeAliases) {
        this.registry = registry;
        this.translations = translations;
        this.typeAliases = typeAliases;
    }

    /// @param type the generic type of a source property
    /// @return the TypeScript type, `any` when the type cannot be mapped
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
                    if (TS_PRIMITIVES.contains(translatedRaw) || translatedRaw.endsWith("[]")) {
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
            if (translations.containsKey(originalFqn) && !translations.get(originalFqn).equals(originalFqn)) {
                return translateTarget(translations.get(originalFqn));
            }
            if (registry.isRegistered(clazz) || clazz.isEnum()) {
                return registry.isRegistered(clazz)
                        ? registry.getTargetName(clazz).flatName()
                        : clazz.getSimpleName();
            }
            return wellKnown(originalFqn).orElse("any");
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

    private String translateTarget(String target) {
        if (target.endsWith("[]")) {
            return "%s[]".formatted(translateTarget(target.substring(0, target.length() - 2)));
        }
        if (typeAliases.containsKey(target)) {
            return simpleName(target);
        }
        if (registry.isRegistered(target)) {
            return registry.getTargetName(target).flatName();
        }
        return wellKnown(target).orElseGet(() -> simpleName(target));
    }

    private static Optional<String> wellKnown(String fqn) {
        return Optional.ofNullable(switch (fqn) {
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
                null;
        });
    }

    /// @param fqn a binary or canonical class name
    /// @return the name after the last `.` or `$`, `null` for `null`
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
