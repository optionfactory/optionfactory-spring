package net.optionfactory.spring.data.jpa.filtering.filters;

import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;
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
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum.InEnumFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum.RepeatableInEnum;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;

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

    Class<? extends Enum<?>> type();

    String property();

    boolean nullable() default false;

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableInEnum {

        InEnum[] value();
    }

    public static class InEnumFilter implements Filter {

        private final String name;
        private final String property;
        private final boolean nullable;
        private final Class<? extends Enum> type;

        public InEnumFilter(InEnum annotation, EntityType<?> entityType) {
            this.name = annotation.name();
            this.property = annotation.property();
            this.nullable = annotation.nullable();
            this.type = annotation.type();
            Filters.ensurePropertyOfAnyType(annotation, entityType, property, type);
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            final boolean hasNull = Stream.of(values).anyMatch(Objects::isNull);
            Filters.ensure(!hasNull || nullable, "Null enum filter values not allowed");
            final Set<Enum> requested = Stream.of(values)
                    .filter(Objects::nonNull)
                    .map(value -> Enum.valueOf(type, value))
                    .collect(Collectors.toSet());
            final Path<Object> enumProperty = Filters.traverseProperty(root, this.property);
            if (requested.isEmpty()) {
                return hasNull ? enumProperty.isNull() : builder.disjunction();
            }
            return hasNull ? builder.or(enumProperty.isNull(), enumProperty.in(requested)) : enumProperty.in(requested);
        }

        @Override
        public String name() {
            return name;
        }
    }
}
