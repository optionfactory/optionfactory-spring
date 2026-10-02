package net.optionfactory.spring.upstream.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

/// Finds the [net.optionfactory.spring.upstream.Upstream] annotations that apply to an endpoint.
///
/// An annotation applies when it is on the method itself or, failing that, on the nearest interface of
/// the hierarchy rooted at the proxied interface, searched breadth first: the proxied interface, then
/// its direct super-interfaces in declaration order, then theirs. The proxied interface matters
/// because it can be a subinterface of the one declaring the method, and carry the annotation for
/// inherited methods.
///
/// Annotations are never merged: the closest declaration wins as a whole. Meta-annotations are not
/// considered.
public class Annotations {

    /// Finds a repeatable annotation on the method or, when the method has none, on the nearest
    /// interface of the hierarchy of `rootIface` that has some.
    ///
    /// @param <T> the annotation type
    /// @param m the method
    /// @param rootIface the proxied interface, which may be a subinterface of the one declaring `m`
    /// @param annotation the repeatable annotation type
    /// @return the annotations of the closest declaration, in declaration order; empty when none is found
    public static <T extends Annotation> List<T> closestRepeatable(Method m, Class<?> rootIface, Class<T> annotation) {
        final var manns = m.getAnnotationsByType(annotation);
        if (manns.length > 0) {
            return List.of(manns);
        }
        return closestRepeatable(rootIface, annotation);
    }

    /// Finds a repeatable annotation on the method or on the hierarchy of its declaring interface.
    ///
    /// @param <T> the annotation type
    /// @param m the method
    /// @param annotation the repeatable annotation type
    /// @return the annotations of the closest declaration, in declaration order; empty when none is found
    public static <T extends Annotation> List<T> closestRepeatable(Method m, Class<T> annotation) {
        return closestRepeatable(m, m.getDeclaringClass(), annotation);
    }

    /// Finds a repeatable annotation on the nearest interface of the hierarchy of `rootIface` that has
    /// some.
    ///
    /// @param <T> the annotation type
    /// @param rootIface the interface to start from
    /// @param annotation the repeatable annotation type
    /// @return the annotations of the closest declaration, in declaration order; empty when none is found
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

    /// Finds an annotation on the method or, when the method lacks it, on the nearest interface of the
    /// hierarchy of `rootIface`.
    ///
    /// @param <T> the annotation type
    /// @param m the method
    /// @param rootIface the proxied interface, which may be a subinterface of the one declaring `m`
    /// @param annotation the annotation type
    /// @return the closest annotation, or empty when none is found
    public static <T extends Annotation> Optional<T> closest(Method m, Class<?> rootIface, Class<T> annotation) {
        final var mann = m.getAnnotation(annotation);
        if (mann != null) {
            return Optional.of(mann);
        }
        return closest(rootIface, annotation);
    }

    /// Finds an annotation on the method or on the hierarchy of its declaring interface.
    ///
    /// @param <T> the annotation type
    /// @param m the method
    /// @param annotation the annotation type
    /// @return the closest annotation, or empty when none is found
    public static <T extends Annotation> Optional<T> closest(Method m, Class<T> annotation) {
        return closest(m, m.getDeclaringClass(), annotation);
    }

    /// Finds an annotation on the nearest interface of the hierarchy of `rootIface`.
    ///
    /// @param <T> the annotation type
    /// @param rootIface the interface to start from
    /// @param annotation the annotation type
    /// @return the closest annotation, or empty when none is found
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
