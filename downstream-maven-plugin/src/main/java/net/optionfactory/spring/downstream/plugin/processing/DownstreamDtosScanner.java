package net.optionfactory.spring.downstream.plugin.processing;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.optionfactory.spring.downstream.Downstream;
import net.optionfactory.spring.downstream.plugin.processing.DownstreamMappingsResolver.Type;
import net.optionfactory.spring.downstream.plugin.processing.DownstreamMethodsScanner.AnnotatedMethod;

public class DownstreamDtosScanner {

    private final String sourcePackage;
    private final String targetClientName;

    public DownstreamDtosScanner(String sourcePackage, String targetClientName) {
        this.sourcePackage = sourcePackage;
        this.targetClientName = targetClientName;
    }

    public Map<Class<?>, Type> scan(List<AnnotatedMethod> methods) {
        final var targetMethods = methods.stream().filter(m -> {
            final var clients = m.annotation().clients();
            return clients.length == 0 || Stream.of(clients).anyMatch(c -> c.equals(targetClientName));
        }).toList();

        final var result = new HashMap<Class<?>, Type>();
        for (final var am : targetMethods) {
            registerIfDto(result, am.method().getAnnotatedReturnType());
            for (final var param : am.method().getParameters()) {
                registerIfDto(result, param.getAnnotatedType());
            }
        }
        return result;
    }

    private void registerIfDto(Map<Class<?>, Type> result, AnnotatedType annotatedType) {
        if (annotatedType == null || annotatedType.isAnnotationPresent(Downstream.Ignore.class)) {
            return;
        }

        if (annotatedType instanceof AnnotatedParameterizedType apt) {
            for (final var arg : apt.getAnnotatedActualTypeArguments()) {
                registerIfDto(result, arg);
            }
            if (apt.getType() instanceof ParameterizedType pType && pType.getRawType() instanceof Class<?> clazz) {
                processClassIfDto(result, clazz);
            }
            return;
        }

        if (!(annotatedType.getType() instanceof Class<?> clazz)) {
            return;
        }
        var elementClass = clazz;
        while (elementClass.isArray()) {
            elementClass = elementClass.getComponentType();
        }

        processClassIfDto(result, elementClass);
    }

    private void processClassIfDto(Map<Class<?>, Type> result, Class<?> clazz) {
        if (!clazz.getName().startsWith(sourcePackage) || clazz.isAnnotation()) {
            return;
        }

        if (clazz.isEnum()) {
            result.put(clazz, Type.ENUM);
            return;
        }

        if (result.put(clazz, Type.DTO) != null) {
            return;
        }

        for (final var field : clazz.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            registerIfDto(result, field.getAnnotatedType());
        }
    }

}
