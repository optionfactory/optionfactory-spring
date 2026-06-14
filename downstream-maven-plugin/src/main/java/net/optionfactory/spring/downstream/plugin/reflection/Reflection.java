package net.optionfactory.spring.downstream.plugin.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.optionfactory.spring.downstream.Downstream;

public class Reflection {

    public static Deque<Class<?>> superclasses(Class<?> clazz, Class<?> stop) {
        final var classes = new ArrayDeque<Class<?>>();
        while (clazz != null && clazz != stop) {
            classes.addFirst(clazz);
            clazz = clazz.getSuperclass();
        }
        return classes;
    }

    public record CandidateField(String name, Type type, AnnotatedType annotatedType, boolean nullable, boolean optional) {

    }

    public static List<CandidateField> candidateFields(Class<?> clazz, Class<?> stop) {
        return superclasses(clazz, stop)
                .stream()
                .flatMap(c -> Stream.of(c.getDeclaredFields()))
                .filter(f -> !f.isSynthetic() && !f.isAnnotationPresent(Downstream.Ignore.class))
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers()))
                .map(f -> new CandidateField(
                f.getName(),
                f.getGenericType(),
                f.getAnnotatedType(),
                hasNullableAnnotation(f.getAnnotations()) || hasNullableAnnotation(f.getAnnotatedType().getAnnotations()),
                f.getType() == Optional.class
        ))
                .toList();
    }

    private static boolean hasNullableAnnotation(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .anyMatch(a -> a.annotationType().getSimpleName().equals("Nullable"));
    }
}
