package net.optionfactory.spring.downstream.plugin.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public record CandidateField(String name, Type type, AnnotatedType annotatedType, boolean nullable, boolean nonNull, boolean optional) {

    }

    public static List<CandidateField> candidateFields(Class<?> clazz, Class<?> stop) {
        final var candidates = new LinkedHashMap<String, CandidateField>();

        if (clazz.isRecord()) {
            for (final var comp : clazz.getRecordComponents()) {
                if (comp.isAnnotationPresent(Downstream.Ignore.class)) {
                    continue;
                }
                boolean isNullable = hasNullableAnnotation(comp.getAnnotations()) || hasNullableAnnotation(comp.getAnnotatedType().getAnnotations());
                boolean isNonNull = hasNonNullAnnotation(comp.getAnnotations()) || hasNonNullAnnotation(comp.getAnnotatedType().getAnnotations());

                candidates.put(comp.getName(), new CandidateField(
                        comp.getName(),
                        comp.getGenericType(),
                        comp.getAnnotatedType(),
                        isNullable,
                        isNonNull,
                        comp.getType() == Optional.class
                ));
            }
            return new ArrayList<>(candidates.values());
        }

        for (final Class<?> c : superclasses(clazz, stop)) {

            final Map<String, Field> declaredFields = new LinkedHashMap<>();
            for (final Field f : c.getDeclaredFields()) {
                declaredFields.put(f.getName(), f);
            }

            for (final Field f : declaredFields.values()) {
                if (f.isSynthetic() || f.isAnnotationPresent(Downstream.Ignore.class)) {
                    continue;
                }
                if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) {
                    continue;
                }
                if (!Modifier.isPublic(f.getModifiers())) {
                    continue;
                }

                boolean isNullable = hasNullableAnnotation(f.getAnnotations()) || hasNullableAnnotation(f.getAnnotatedType().getAnnotations());
                boolean isNonNull = hasNonNullAnnotation(f.getAnnotations()) || hasNonNullAnnotation(f.getAnnotatedType().getAnnotations());

                candidates.put(f.getName(), new CandidateField(
                        f.getName(),
                        f.getGenericType(),
                        f.getAnnotatedType(),
                        isNullable,
                        isNonNull,
                        f.getType() == Optional.class
                ));
            }

            for (final Method m : c.getDeclaredMethods()) {
                if (m.isSynthetic() || m.isAnnotationPresent(Downstream.Ignore.class)) {
                    continue;
                }
                if (Modifier.isStatic(m.getModifiers()) || m.getParameterCount() > 0 || m.getReturnType() == void.class) {
                    continue;
                }
                if (!Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                if (m.getName().equals("getClass")) {
                    continue;
                }

                String propName = null;
                if (m.getName().startsWith("get") && m.getName().length() > 3) {
                    propName = Character.toLowerCase(m.getName().charAt(3)) + m.getName().substring(4);
                } else if (m.getName().startsWith("is") && m.getName().length() > 2 && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                    propName = Character.toLowerCase(m.getName().charAt(2)) + m.getName().substring(3);
                }

                if (propName != null) {
                    boolean isNullable = hasNullableAnnotation(m.getAnnotations()) || hasNullableAnnotation(m.getAnnotatedReturnType().getAnnotations());
                    boolean isNonNull = hasNonNullAnnotation(m.getAnnotations()) || hasNonNullAnnotation(m.getAnnotatedReturnType().getAnnotations());

                    final Field backingField = declaredFields.get(propName);
                    if (backingField != null) {
                        isNullable |= hasNullableAnnotation(backingField.getAnnotations()) || hasNullableAnnotation(backingField.getAnnotatedType().getAnnotations());
                        isNonNull |= hasNonNullAnnotation(backingField.getAnnotations()) || hasNonNullAnnotation(backingField.getAnnotatedType().getAnnotations());
                    }

                    final CandidateField existing = candidates.get(propName);
                    if (existing != null) {
                        isNullable |= existing.nullable();
                        isNonNull |= existing.nonNull();
                    }

                    candidates.put(propName, new CandidateField(
                            propName,
                            m.getGenericReturnType(),
                            m.getAnnotatedReturnType(),
                            isNullable,
                            isNonNull,
                            m.getReturnType() == Optional.class
                    ));
                }
            }
        }
        return new ArrayList<>(candidates.values());
    }

    private static boolean hasNullableAnnotation(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .anyMatch(a -> a.annotationType().getSimpleName().equals("Nullable"));
    }

    private static boolean hasNonNullAnnotation(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .anyMatch(a -> a.annotationType().getSimpleName().equals("NonNull") || a.annotationType().getSimpleName().equals("NotNull"));
    }
}
