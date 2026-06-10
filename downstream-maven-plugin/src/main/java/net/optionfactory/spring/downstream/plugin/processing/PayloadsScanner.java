package net.optionfactory.spring.downstream.plugin.processing;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.optionfactory.spring.downstream.Downstream;
import net.optionfactory.spring.downstream.plugin.processing.AnnotatedMethodsScanner.AnnotatedMethod;

public class PayloadsScanner {

    private final String sourcePackage;
    private final String targetClientName;

    public PayloadsScanner(String sourcePackage, String targetClientName) {
        this.sourcePackage = sourcePackage;
        this.targetClientName = targetClientName;
    }

    public enum PayloadType {
        ENUM,
        DTO;
    }

    public Map<Class<?>, PayloadType> scan(List<AnnotatedMethod> methods) {
        final var targetMethods = methods.stream().filter(m -> {
            final var clients = m.annotation().clients();
            return clients.length == 0 || targetClientName == null || Stream.of(clients).anyMatch(c -> c.equals(targetClientName));
        }).toList();

        final var result = new HashMap<Class<?>, PayloadType>();
        for (final var am : targetMethods) {
            registerIfPayload(result, am.method().getAnnotatedReturnType());
            for (final var param : am.method().getParameters()) {
                registerIfPayload(result, param.getAnnotatedType());
            }
        }
        return result;
    }

    private void registerIfPayload(Map<Class<?>, PayloadType> result, AnnotatedType annotatedType) {
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
        if (!(annotatedType.getType() instanceof Class<?> clazz)) {
            return;
        }
        var elementClass = clazz;
        while (elementClass.isArray()) {
            elementClass = elementClass.getComponentType();
        }

        processClassIfPayload(result, elementClass);
    }

    private void processClassIfPayload(Map<Class<?>, PayloadType> result, Class<?> clazz) {
        if (!clazz.getName().startsWith(sourcePackage) || clazz.isAnnotation()) {
            return;
        }
        if (clazz.isEnum()) {
            result.put(clazz, PayloadType.ENUM);
            return;
        }
        if (result.put(clazz, PayloadType.DTO) != null) {
            return;
        }

        for (final Class<?> nested : clazz.getDeclaredClasses()) {
            processClassIfPayload(result, nested);
        }

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (final var field : current.getDeclaredFields()) {
                if (field.isSynthetic()) {
                    continue;
                }
                registerIfPayload(result, field.getAnnotatedType());
            }
            current = current.getSuperclass();
        }
    }
}
