package net.optionfactory.spring.downstream.plugin.discovery;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.optionfactory.spring.downstream.Downstream;
import net.optionfactory.spring.downstream.plugin.reflection.Reflection;

/// Collects the payload types an endpoint exchanges: the DTOs and enums to generate.
///
/// Starting from the return type and the parameters of every endpoint, it follows type arguments,
/// array components, the [candidate fields][Reflection#candidateFields] of each collected class
/// and its nested classes, so a nested class is collected even when no field references it.
/// Enums are collected but not walked into. Cycles are walked once.
///
/// Only classes whose package starts with the source package are collected, so `String`, `List`
/// or a framework type stop the walk, as do annotation types, classes annotated with
/// `@Downstream.Ignore` and parameters annotated with it. The match is a plain prefix match on the
/// package name: `com.example` also matches `com.examples`.
public class Payloads {

    private final String sourcePackage;

    /// @param sourcePackage the package prefix of the payload types to collect
    public Payloads(String sourcePackage) {
        this.sourcePackage = sourcePackage;
    }

    /// @param endpoints the endpoint methods
    /// @return the payload types reachable from the endpoints, empty when there is none
    public Set<Class<?>> discover(List<Method> endpoints) {
        final var result = new HashSet<Class<?>>();
        for (final var method : endpoints) {
            registerIfPayload(result, method.getAnnotatedReturnType());
            for (final var param : method.getParameters()) {
                if (param.isAnnotationPresent(Downstream.Ignore.class)) {
                    continue;
                }
                registerIfPayload(result, param.getAnnotatedType());
            }
        }
        return result;
    }

    private void registerIfPayload(Set<Class<?>> result, AnnotatedType annotatedType) {
        if (annotatedType == null || annotatedType.isAnnotationPresent(Downstream.Ignore.class)) {
            return;
        }
        if (annotatedType instanceof AnnotatedParameterizedType apt) {
            for (final var arg : apt.getAnnotatedActualTypeArguments()) {
                registerIfPayload(result, arg);
            }
            if (apt.getType() instanceof ParameterizedType pType && pType.getRawType() instanceof Class<?> clazz) {
                processClassIfPayload(result, clazz);
            }
            return;
        }
        if (annotatedType.getType() instanceof Class<?> clazz) {
            var elementClass = clazz;
            while (elementClass.isArray()) {
                elementClass = elementClass.getComponentType();
            }
            processClassIfPayload(result, elementClass);
        }
    }

    private void processClassIfPayload(Set<Class<?>> result, Class<?> clazz) {
        if (clazz.isAnnotationPresent(Downstream.Ignore.class)) {
            return;
        }
        if (!clazz.getPackageName().startsWith(sourcePackage) || clazz.isAnnotation()) {
            return;
        }
        if (!result.add(clazz)) {
            return;
        }
        if (clazz.isEnum()) {
            return;
        }
        for (final Class<?> nested : clazz.getDeclaredClasses()) {
            processClassIfPayload(result, nested);
        }

        Reflection.candidateFields(clazz, Object.class)
                .forEach(field -> registerIfPayload(result, field.annotatedType()));
    }

}
