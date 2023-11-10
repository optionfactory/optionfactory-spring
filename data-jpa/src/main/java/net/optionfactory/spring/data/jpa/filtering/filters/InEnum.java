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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum.InEnumFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum.RepeatableInEnum;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;

/**
 * Filters an enum property with a set of accepted values. Filter arguments list
 * must contain the enum constants that have to be accepted. With no argument
 * given, the filtered result will always be empty. If
 * {@link InEnum#nullable() nullable} is true, then {@code null} arguments can
 * be passed in, that will match {@code NULL} values in nullable columns.
 */
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(InEnumFilter.class)
@Repeatable(RepeatableInEnum.class)
public @interface InEnum {

    String name();

    QueryMode mode() default QueryMode.JOIN;

    Class<? extends Enum<?>> type();

    String path();

    boolean nullable() default false;

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableInEnum {

        InEnum[] value();
    }

    public static class InEnumFilter implements TraversalFilter<Enum<?>> {

        private final String name;
        private final QueryMode mode;
        private final boolean nullable;
        private final Class<? extends Enum> type;
        private final Traversal traversal;

        public InEnumFilter(InEnum annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.mode = annotation.mode();
            this.nullable = annotation.nullable();
            this.type = annotation.type();
            this.traversal = Filters.traversal(annotation, entity, annotation.path());
            Filters.ensurePropertyOfAnyType(annotation, entity, traversal, type);
        }

        @Override
        public Predicate condition(Root<?> root, Path<Enum<?>> path, CriteriaBuilder builder, String[] values) {
            final boolean hasNull = Stream.of(values).anyMatch(Objects::isNull);
            Filters.ensure(!hasNull || nullable, name, root, "null enum filter values is not whitelisted");
            final Set<Enum> requested = Stream.of(values)
                    .filter(Objects::nonNull)
                    .map(value -> Enum.valueOf(type, value))
                    .collect(Collectors.toSet());
            if (requested.isEmpty()) {
                return hasNull ? path.isNull() : builder.disjunction();
            }
            return hasNull ? builder.or(path.isNull(), path.in(requested)) : path.in(requested);
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

        public static String[] in(Enum<?>... values) {
            return Stream.of(values)
                    .map(ev -> ev == null ? null : ev.name())
                    .toArray(i -> new String[i]);
        }

    }

}
