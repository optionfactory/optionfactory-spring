package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.Sortable;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.util.Pair;

/// Reads the filter and sorter whitelists off an entity class, as the repository does when it is
/// created.
///
/// Repeated annotations are read through their containers. The annotations are those present on
/// the entity class: a superclass contributes only `@Inherited` ones, which the built-in
/// annotations are not.
public interface Repositories {

    /// @param <T> the entity type
    /// @param ei the entity metadata
    /// @param em the entity manager, providing the metamodel and the dialect
    /// @return a filter for each annotation meta-annotated with [WhitelistedFilter], by name
    /// @throws IllegalStateException when two filters share a name, when a filter cannot be
    /// instantiated, or, as an [InvalidFilterConfiguration], when one is misconfigured
    public static <T> Map<String, Filter> allowedFilters(JpaEntityInformation<T, ?> ei, EntityManager em) {
        return Stream
                .of(ei.getJavaType().getAnnotations())
                .flatMap(repeatableAnnotation -> flattenRepeatables(repeatableAnnotation))
                .filter(annotation -> null != AnnotationUtils.findAnnotation(annotation.annotationType(), WhitelistedFilter.class))
                .map(annotation -> createFilterFromAnnotation(annotation, ei, em))
                .collect(Collectors.toMap(fspec -> fspec.name(), fspec -> fspec));
    }

    /// @param <T> the entity type
    /// @param ei the entity metadata
    /// @param em the entity manager, providing the metamodel
    /// @return the resolved path of each [Sortable], by name
    /// @throws IllegalStateException when two sorters share a name, or, as an
    /// [InvalidSortConfiguration], when one is misconfigured
    public static <T> Map<String, Traversal> allowedSorters(JpaEntityInformation<T, ?> ei, EntityManager em) {
        return Stream
                .of(ei.getJavaType().getAnnotations())
                .flatMap(repeatableAnnotation -> flattenRepeatables(repeatableAnnotation))
                .filter(annotation -> annotation.annotationType().equals(Sortable.class))
                .map(annotation -> createSorterFromAnnotation(annotation, ei, em))
                .collect(Collectors.toMap(sspec -> sspec.getFirst(), sspec -> sspec.getSecond()));
    }

    private static Stream<Annotation> flattenRepeatables(Annotation repeatableAnnotation) {
        final Object value = AnnotationUtils.getValue(repeatableAnnotation);
        if (value instanceof Annotation[] annotations) {
            return Stream.of(annotations);
        }
        return Stream.of(repeatableAnnotation);
    }

    /// Instantiates the filter an annotation whitelists, through the only public constructor of the
    /// [WhitelistedFilter] implementation whose parameters are all among: the annotation, the
    /// `JpaEntityInformation`, the `EntityManager`, the `EntityManagerFactory` and the `EntityType`.
    ///
    /// @param <T> the entity type
    /// @param annotation the whitelisting annotation
    /// @param ei the entity metadata
    /// @param em the entity manager
    /// @return the filter
    /// @throws IllegalStateException when there is no such constructor or more than one, or the
    /// constructor fails with a checked exception; a runtime exception thrown by the constructor is
    /// propagated as is
    public static <T> Filter createFilterFromAnnotation(Annotation annotation, JpaEntityInformation<T, ?> ei, EntityManager em) throws IllegalStateException {
        final Class<? extends Filter> filterClass = AnnotatedElementUtils.findMergedAnnotation(AnnotatedElementUtils.forAnnotations(annotation), WhitelistedFilter.class).value();
        try {
            final Map<Class<?>, Object> typeToArgument = new HashMap<>();
            typeToArgument.put(annotation.annotationType(), annotation);
            typeToArgument.put(JpaEntityInformation.class, ei);
            typeToArgument.put(EntityManager.class, em);
            typeToArgument.put(EntityManagerFactory.class, em.getEntityManagerFactory());
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
            if (ex.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(ex);
        } catch (IllegalAccessException | InstantiationException | SecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// @param <T> the entity type
    /// @param annotation a [Sortable] annotation
    /// @param ei the entity metadata
    /// @param em the entity manager, providing the metamodel
    /// @return the sorter name and its resolved path
    /// @throws InvalidSortConfiguration when the path does not resolve or crosses a collection
    public static <T> Pair<String, Traversal> createSorterFromAnnotation(Annotation annotation, JpaEntityInformation<T, ?> ei, EntityManager em) throws IllegalStateException {
        final var ma = AnnotatedElementUtils.findMergedAnnotation(AnnotatedElementUtils.forAnnotations(annotation), Sortable.class);
        final var entity = em.getMetamodel().entity(ei.getJavaType());
        return Pair.of(ma.name(), Sorters.traversal(entity, ma.name(), ma.path()));
    }
}
