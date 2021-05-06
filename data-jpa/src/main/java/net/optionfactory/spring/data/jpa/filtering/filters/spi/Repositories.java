package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javassist.Modifier;
import javax.persistence.EntityManager;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.Sortable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.util.Pair;

public interface Repositories {

    public static <T> Map<String, Filter> allowedFilters(JpaEntityInformation<T, ?> ei, EntityManager em) {
        return Stream
                .of(ei.getJavaType().getAnnotations())
                .flatMap(repeatableAnnotation -> flattenRepeatables(repeatableAnnotation))
                .filter(annotation -> null != AnnotationUtils.findAnnotation(annotation.annotationType(), WhitelistedFilter.class))
                .map(annotation -> createFilterFromAnnotation(annotation, ei, em))
                .collect(Collectors.toMap(fspec -> fspec.name(), fspec -> fspec));
    }

    public static <T> Map<String, String> allowedSorters(JpaEntityInformation<T, ?> ei, EntityManager em) {
        return Stream
                .of(ei.getJavaType().getAnnotations())
                .flatMap(repeatableAnnotation -> flattenRepeatables(repeatableAnnotation))
                .filter(annotation -> annotation.annotationType().equals(Sortable.class))
                .map(annotation -> createSorterFromAnnotation(annotation, ei, em))
                .collect(Collectors.toMap(sspec -> sspec.getFirst(), sspec -> sspec.getSecond()));
    }

    private static Stream<Annotation> flattenRepeatables(Annotation repeatableAnnotation) {
        final Object value = AnnotationUtils.getValue(repeatableAnnotation);
        if (value instanceof Annotation[]) {
            return Stream.of((Annotation[]) value);
        }
        return Stream.of(repeatableAnnotation);
    }

    public static <T> Filter createFilterFromAnnotation(Annotation annotation, JpaEntityInformation<T, ?> ei, EntityManager em) throws IllegalStateException {
        final Class<? extends Filter> filterClass = AnnotatedElementUtils.findMergedAnnotation(AnnotatedElementUtils.forAnnotations(annotation), WhitelistedFilter.class).value();
        try {
            final Map<Class<?>, Object> typeToArgument = new HashMap<>();
            typeToArgument.put(annotation.annotationType(), annotation);
            typeToArgument.put(JpaEntityInformation.class, ei);
            typeToArgument.put(EntityManager.class, em);
            typeToArgument.put(EntityType.class, em.getMetamodel().entity(ei.getJavaType()));

            final List<Constructor<?>> candidates = Stream.of(filterClass.getConstructors())
                    .filter(ctor -> Modifier.isPublic(ctor.getModifiers()))
                    .filter(ctor -> Stream.of(ctor.getParameterTypes()).allMatch(pt -> typeToArgument.containsKey(pt)))
                    .collect(Collectors.toList());

            if (candidates.isEmpty()) {
                throw new IllegalStateException(String.format("No suitable public constructor for Filter %s", filterClass));
            }
            if (candidates.size() > 1) {
                throw new IllegalStateException(String.format("Too many suitable public constructors for Filter %s", filterClass));
            }

            final Constructor<?> constructor = candidates.get(0);
            final Object[] arguments = Stream.of(constructor.getParameterTypes()).map(pt -> typeToArgument.get(pt)).toArray();
            return (Filter) constructor.newInstance(arguments);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            }
            throw new IllegalStateException(ex);
        } catch (IllegalAccessException | InstantiationException | SecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static <T> Pair<String, String> createSorterFromAnnotation(Annotation annotation, JpaEntityInformation<T, ?> ei, EntityManager em) throws IllegalStateException {
        final var ma = AnnotatedElementUtils.findMergedAnnotation(AnnotatedElementUtils.forAnnotations(annotation), Sortable.class);
        return Pair.of(ma.name(), ma.path());
    }
}
