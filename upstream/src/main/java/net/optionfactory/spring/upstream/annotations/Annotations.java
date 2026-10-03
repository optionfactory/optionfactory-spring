package net.optionfactory.spring.upstream.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.annotation.RepeatableContainers;

/// Finds the [net.optionfactory.spring.upstream.Upstream] annotations that apply to an endpoint.
///
/// An annotation configuring a whole client is taken from the nearest interface of the hierarchy
/// rooted at the proxied interface, searched breadth first: the proxied interface, then its direct
/// super-interfaces in declaration order, then theirs.
///
/// An annotation configuring an endpoint is taken from the method itself or, failing that, from the
/// nearest interface found in two passes: first the interfaces between the proxied interface and the
/// one declaring the method, which inherit the method and override what they inherit, breadth first
/// from the proxied interface down to the declaring one; then the declaring interface's own
/// hierarchy, breadth first. The proxied interface matters because it can be a subinterface of the
/// one declaring the method, and carry the annotation for inherited methods. Interfaces unrelated
/// to the declaring one are never searched, so a proxied interface aggregating sibling interfaces
/// never lets one sibling's annotations configure the endpoints of another, and an ancestor of the
/// declaring interface never wins over it.
///
/// The annotations of "the method itself" come from its most specific declaration carrying some: a
/// method redeclared by a subinterface (e.g. to narrow its return type) keeps the method-level
/// annotations of the declaration it overrides, unless it declares its own, which replace them.
///
/// Annotations are never merged: the closest declaration wins as a whole. Meta-annotations are not
/// considered.
public class Annotations {

    /// Finds the most specific declaration of `m` carrying `annotation`: the method itself or, when it
    /// lacks it, the nearest method it overrides that has it, generic overrides included.
    ///
    /// @param m the method
    /// @param annotation the annotation type, repeatable or not
    /// @return the declaration carrying the annotation, or empty when none does
    public static Optional<Method> declaration(Method m, Class<? extends Annotation> annotation) {
        return MergedAnnotations.from(m, SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.standardRepeatables())
                .stream(annotation)
                .filter(a -> a.getDistance() == 0)
                .min(Comparator.comparingInt(MergedAnnotation::getAggregateIndex))
                .map(a -> (Method) a.getSource());
    }

    /// Finds an annotation on the most specific declaration of `m` carrying it.
    ///
    /// @param <T> the annotation type
    /// @param m the method
    /// @param annotation the annotation type
    /// @return the annotation, or empty when no declaration of the method carries it
    public static <T extends Annotation> Optional<T> onMethod(Method m, Class<T> annotation) {
        return declaration(m, annotation).map(d -> d.getAnnotation(annotation));
    }

    /// Finds a repeatable annotation on the most specific declaration of `m` carrying some.
    ///
    /// @param <T> the annotation type
    /// @param m the method
    /// @param annotation the repeatable annotation type
    /// @return the annotations of that declaration, in declaration order; empty when none carries any
    public static <T extends Annotation> List<T> onMethodRepeatable(Method m, Class<T> annotation) {
        return declaration(m, annotation).map(d -> List.of(d.getAnnotationsByType(annotation))).orElse(List.of());
    }

    /// Finds a repeatable annotation on the method or, when the method has none, on the nearest
    /// interface that has some, searched in the two passes described in [Annotations].
    ///
    /// @param <T> the annotation type
    /// @param m the method
    /// @param rootIface the proxied interface, which may be a subinterface of the one declaring `m`
    /// @param annotation the repeatable annotation type
    /// @return the annotations of the closest declaration, in declaration order; empty when none is found
    public static <T extends Annotation> List<T> closestRepeatable(Method m, Class<?> rootIface, Class<T> annotation) {
        final var manns = onMethodRepeatable(m, annotation);
        if (!manns.isEmpty()) {
            return manns;
        }
        final Predicate<Class<?>> annotated = i -> i.getAnnotationsByType(annotation).length > 0;
        return nearest(rootIface, inheriting(m).and(annotated))
                .or(() -> nearest(m.getDeclaringClass(), annotated))
                .map(i -> List.of(i.getAnnotationsByType(annotation)))
                .orElse(List.of());
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
        return nearest(rootIface, i -> i.getAnnotationsByType(annotation).length > 0)
                .map(i -> List.of(i.getAnnotationsByType(annotation)))
                .orElse(List.of());
    }

    /// Finds an annotation on the method or, when the method lacks it, on the nearest interface,
    /// searched in the two passes described in [Annotations].
    ///
    /// @param <T> the annotation type
    /// @param m the method
    /// @param rootIface the proxied interface, which may be a subinterface of the one declaring `m`
    /// @param annotation the annotation type
    /// @return the closest annotation, or empty when none is found
    public static <T extends Annotation> Optional<T> closest(Method m, Class<?> rootIface, Class<T> annotation) {
        final var mann = onMethod(m, annotation);
        if (mann.isPresent()) {
            return mann;
        }
        final Predicate<Class<?>> annotated = i -> i.isAnnotationPresent(annotation);
        return nearest(rootIface, inheriting(m).and(annotated))
                .or(() -> nearest(m.getDeclaringClass(), annotated))
                .map(i -> i.getAnnotation(annotation));
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
        return nearest(rootIface, i -> i.isAnnotationPresent(annotation))
                .map(i -> i.getAnnotation(annotation));
    }

    private static Predicate<Class<?>> inheriting(Method m) {
        return i -> m.getDeclaringClass().isAssignableFrom(i);
    }

    private static Optional<Class<?>> nearest(Class<?> rootIface, Predicate<Class<?>> matching) {
        final var q = new ArrayDeque<Class<?>>();
        q.add(rootIface);
        while (!q.isEmpty()) {
            final var iface = q.pop();
            if (matching.test(iface)) {
                return Optional.of(iface);
            }
            for (final var i : iface.getInterfaces()) {
                q.add(i);
            }
        }
        return Optional.empty();
    }
}
