package net.optionfactory.spring.upstream.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public class Annotations {


    public static <T extends Annotation> List<T> closestRepeatable(Method m, Class<T> annotation) {
        final var manns = m.getAnnotationsByType(annotation);
        if (manns.length > 0) {
            return List.of(manns);
        }
        return closestRepeatable(m.getDeclaringClass(), annotation);
    }

    public static <T extends Annotation> List<T> closestRepeatable(Class<?> k, Class<T> annotation) {
        for (var curr = k; curr != null; curr = curr.getSuperclass()) {
            var canns = curr.getAnnotationsByType(annotation);
            if (canns.length > 0) {
                return List.of(canns);
            }
        }
        return List.of();
    }

    public static <T extends Annotation> Optional<T> closest(Method m, Class<T> annotation) {
        final var mann = m.getAnnotation(annotation);
        if (mann != null) {
            return Optional.of(mann);
        }
        return closest(m.getDeclaringClass(), annotation);
    }

    public static <T extends Annotation> Optional<T> closest(Class<?> k, Class<T> annotation) {
        for (var curr = k; curr != null; curr = curr.getSuperclass()) {
            var cann = curr.getAnnotation(annotation);
            if (cann != null) {
                return Optional.of(cann);
            }
        }
        return Optional.empty();
    }
}
