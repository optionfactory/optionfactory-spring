package net.optionfactory.spring.data.jpa.filtering.filters;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.InList.InListFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.InList.RepeatableInList;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Values;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;

/**
 * Filters a property with a set of accepted values. The property type should be
 * a primitive type (exception made for {@code boolean}, supported by
 * {@link BooleanCompare}), {@link String} or {@link Number}.With no argument
 * given, the filtered result will always be empty. If {@code null} arguments
 * are passed in, {@code NULL} values of a nullable column will be matched.
 */
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(InListFilter.class)
@Repeatable(RepeatableInList.class)
public @interface InList {

    String name();

    QueryMode mode() default QueryMode.JOIN;

    String path();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableInList {

        InList[] value();
    }

    public static class InListFilter implements TraversalFilter<Object> {

        private final String name;
        private final QueryMode mode;
        private final Traversal traversal;

        public InListFilter(InList annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.mode = annotation.mode();
            this.traversal = Filters.traversal(annotation, entity, annotation.path());
            Filters.ensurePropertyOfAnyType(annotation, entity, traversal, String.class, Number.class, byte.class, short.class, int.class, long.class, float.class, double.class, char.class);
        }

        @Override
        public Predicate condition(Root<?> root, Path<Object> path, CriteriaBuilder builder, String[] values) {
            if (values.length == 0) {
                return builder.disjunction();
            }
            final Object[] nonNullValues = Stream.of(values)
                    .filter(Objects::nonNull)
                    .map(value -> Values.convert(name, root, value, traversal.attribute.getJavaType()))
                    .toArray();
            final boolean hasNullValues = nonNullValues.length < values.length;
            if (hasNullValues) {
                return builder.or(path.isNull(), path.in(nonNullValues));
            }
            return path.in(nonNullValues);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public QueryMode mode() {
            return mode;
        }

        @Override
        public Traversal traversal() {
            return traversal;
        }
    }

    public static class Filter {

        public static String[] in(String... values) {
            return values;
        }

    }

}
