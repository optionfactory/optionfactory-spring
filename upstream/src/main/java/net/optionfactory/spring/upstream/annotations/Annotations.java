package net.optionfactory.spring.upstream.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

public class Annotations {

    /// Looks the annotation up on the method first, then on the root interface
    /// and its ancestors: [rootIface] is the proxied interface, which may be a
    /// subinterface of the one declaring [m] and carry the annotation there.
    public static <T extends Annotation> List<T> closestRepeatable(Method m, Class<?> rootIface, Class<T> annotation) {
        final var manns = m.getAnnotationsByType(annotation);
        if (manns.length > 0) {
            return List.of(manns);
        }
        return closestRepeatable(rootIface, annotation);
    }

    public static <T extends Annotation> List<T> closestRepeatable(Method m, Class<T> annotation) {
        return closestRepeatable(m, m.getDeclaringClass(), annotation);
    }

    public static <T extends Annotation> List<T> closestRepeatable(Class<?> rootIface, Class<T> annotation) {
        final var q = new ArrayDeque<Class<?>>();
        q.add(rootIface);
        while (!q.isEmpty()) {
            final var iface = q.pop();
            final var ianns = iface.getAnnotationsByType(annotation);
            if (ianns.length > 0) {
                return List.of(ianns);
            }
            for (final var i : iface.getInterfaces()) {
                q.add(i);
            }
        }
        return List.of();
    }

    /// Looks the annotation up on the method first, then on the root interface
    /// and its ancestors: [rootIface] is the proxied interface, which may be a
    /// subinterface of the one declaring [m] and carry the annotation there.
    public static <T extends Annotation> Optional<T> closest(Method m, Class<?> rootIface, Class<T> annotation) {
        final var mann = m.getAnnotation(annotation);
        if (mann != null) {
            return Optional.of(mann);
        }
        return closest(rootIface, annotation);
    }

    public static <T extends Annotation> Optional<T> closest(Method m, Class<T> annotation) {
        return closest(m, m.getDeclaringClass(), annotation);
    }

    public static <T extends Annotation> Optional<T> closest(Class<?> rootIface, Class<T> annotation) {
        final var q = new ArrayDeque<Class<?>>();
        q.add(rootIface);
        while (!q.isEmpty()) {
            final var iface = q.pop();
            final var iann = iface.getAnnotation(annotation);
            if (iann != null) {
                return Optional.of(iann);
            }
            for (final var i : iface.getInterfaces()) {
                q.add(i);
            }
        }
        return Optional.empty();
    }
}
