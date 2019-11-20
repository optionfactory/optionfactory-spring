package net.optionfactory.data.jpa.filtering.filters;

import net.optionfactory.data.jpa.filtering.Filter;
import net.optionfactory.data.jpa.filtering.filters.spi.WhitelistedFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.data.jpa.filtering.filters.InEnum.InEnumFilter;
import net.optionfactory.data.jpa.filtering.filters.InEnum.RepeatableInEnum;
import net.optionfactory.data.jpa.filtering.filters.spi.Filters;

@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(InEnumFilter.class)
@Repeatable(RepeatableInEnum.class)
public @interface InEnum {

    String name();

    Class<? extends Enum<?>> type();

    String property();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableInEnum {

        InEnum[] value();
    }

    public static class InEnumFilter implements Filter {

        private final String name;
        private final String property;
        private final Class<? extends Enum<?>> type;

        public InEnumFilter(InEnum ie, EntityType<?> entityType) {
            this.name = ie.name();
            this.property = ie.property();
            this.type = ie.type();
            Filters.ensurePropertyOfAnyType(ie, entityType, property, type);
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            @SuppressWarnings("unchecked")
            final Stream<Enum<?>> stream = Stream.of(values).map(v -> {
                final Class<? extends Enum> t = (Class<? extends Enum>) this.type;
                return Enum.valueOf(t, v);
            });
            final Set<Enum<?>> requested = stream.collect(Collectors.toSet());
            if (requested.isEmpty()) {
                return builder.disjunction();
            }
            return Filters.traverseProperty(root, this.property).in(requested);
        }

        @Override
        public String name() {
            return name;
        }
    }
}
