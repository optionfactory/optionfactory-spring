package net.optionfactory.data.jpa.filtering;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javassist.Modifier;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import net.optionfactory.data.jpa.filtering.filters.spi.WhitelistedFilter;
import org.springframework.core.annotation.AnnotatedElementUtils;

public class JpaWhitelistFilteringRepositoryBase<T, ID extends Serializable> extends SimpleJpaRepository<T, ID> {

    private final Map<String, Filter> whitelist;

    public JpaWhitelistFilteringRepositoryBase(JpaEntityInformation<T, ?> ei, EntityManager em) {
        super(ei, em);

        this.whitelist = Stream
                .of(ei.getJavaType().getAnnotations())
                .flatMap(repeatableAnnotation -> flattenRepeatables(repeatableAnnotation))
                .filter(annotation -> null != AnnotationUtils.findAnnotation(annotation.annotationType(), WhitelistedFilter.class))
                .map(annotation -> createFilterFromAnnotation(annotation, ei, em))
                .collect(Collectors.toMap(fspec -> fspec.name(), fspec -> fspec));
    }

    private static Stream<Annotation> flattenRepeatables(Annotation repeatableAnnotation) {
        final Object value = AnnotationUtils.getValue(repeatableAnnotation);
        if (value instanceof Annotation[]) {
            return Stream.of((Annotation[]) value);
        }
        return Stream.of(repeatableAnnotation);
    }

    private static <T> Filter createFilterFromAnnotation(Annotation annotation, JpaEntityInformation<T, ?> ei, EntityManager em) throws IllegalStateException {
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

    public Optional<T> findOne(FilterRequest filters) {
        return findOne(new WhitelistFilteringSpecificationAdapter<>(filters, whitelist));
    }

    public Optional<T> findOne(Specification<T> base, FilterRequest filters) {
        return findOne(Specification.where(base).and(new WhitelistFilteringSpecificationAdapter<>(filters, whitelist)));
    }

    public List<T> findAll(FilterRequest filters) {
        return findAll(new WhitelistFilteringSpecificationAdapter<>(filters, whitelist));
    }

    public List<T> findAll(Specification<T> base, FilterRequest filters) {
        return findAll(Specification.where(base).and(new WhitelistFilteringSpecificationAdapter<>(filters, whitelist)));
    }

    public Page<T> findAll(FilterRequest filters, Pageable pageable) {
        return findAll(new WhitelistFilteringSpecificationAdapter<>(filters, whitelist), pageable);
    }

    public Page<T> findAll(Specification<T> base, FilterRequest filters, Pageable pageable) {
        return findAll(Specification.where(base).and(new WhitelistFilteringSpecificationAdapter<>(filters, whitelist)), pageable);
    }

    public List<T> findAll(FilterRequest filters, Sort sort) {
        return findAll(new WhitelistFilteringSpecificationAdapter<>(filters, whitelist), sort);
    }

    public List<T> findAll(Specification<T> base, FilterRequest filters, Sort sort) {
        return findAll(Specification.where(base).and(new WhitelistFilteringSpecificationAdapter<>(filters, whitelist)), sort);
    }

    public long count(FilterRequest filters) {
        return count(new WhitelistFilteringSpecificationAdapter<>(filters, whitelist));
    }

    public long count(Specification<T> base, FilterRequest filters) {

        return count(Specification.where(base).and(new WhitelistFilteringSpecificationAdapter<>(filters, whitelist)));
    }

    public static class WhitelistFilteringSpecificationAdapter<T> implements Specification<T> {

        private final Map<String, String[]> requested;
        private final Map<String, Filter> whitelisted;

        public WhitelistFilteringSpecificationAdapter(Map<String, String[]> requested, Map<String, Filter> whitelisted) {
            this.requested = requested;
            this.whitelisted = whitelisted;
        }

        @Override
        public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
            final Predicate[] predicates = requested
                    .entrySet()
                    .stream()
                    .map(e -> {
                        final String name = e.getKey();
                        final String values[] = e.getValue();
                        final Filter spec = whitelisted.get(name);
                        if (spec == null) {
                            throw new InvalidFilterRequest(String.format("requested filter '%s' is not configured for root object '%s'", name, root.getJavaType().getSimpleName()));
                        }
                        return spec.toPredicate(root, query, builder, values);
                    })
                    .toArray((size) -> new Predicate[size]);
            return builder.and(predicates);
        }

    }

}
