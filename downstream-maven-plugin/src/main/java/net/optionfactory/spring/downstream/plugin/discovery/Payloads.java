package net.optionfactory.spring.downstream.plugin.discovery;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.optionfactory.spring.downstream.Downstream;
import net.optionfactory.spring.downstream.plugin.reflection.Reflection;

public class Payloads {

    private final String sourcePackage;

    public Payloads(String sourcePackage) {
        this.sourcePackage = sourcePackage;
    }

    public Set<Class<?>> discover(List<Method> endpoints) {
        final var result = new HashSet<Class<?>>();
        for (final var method : endpoints) {
            registerIfPayload(result, method.getAnnotatedReturnType());
            for (final var param : method.getParameters()) {
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
        Reflection.superclasses(clazz, Object.class).stream()
                .flatMap(c -> Stream.of(c.getDeclaredFields()))
                .filter(field -> !field.isSynthetic())
                .forEach(field -> registerIfPayload(result, field.getAnnotatedType()));
    }

}
