package net.optionfactory.data.jpa.filtering.filters;

import net.optionfactory.data.jpa.filtering.Filter;
import net.optionfactory.data.jpa.filtering.filters.spi.WhitelistedFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.stream.Stream;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.data.jpa.filtering.filters.LocalDateCompare.LocalDateCompareFilter;
import net.optionfactory.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.data.jpa.filtering.filters.LocalDateCompare.RepeatableLocalDateCompare;

/**
 * Compares a {@link LocalDate} property.The first argument must be a
 * whitelisted {@link Operator}. The {@link Operator#BETWEEN} accepts two
 * arguments, while the other operators accept a single argument, which format
 * must match the configured {@link #datePattern() datePattern}.
 */
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(LocalDateCompareFilter.class)
@Repeatable(RepeatableLocalDateCompare.class)
public @interface LocalDateCompare {

    public enum Operator {
        EQ, LT, GT, LTE, GTE, BETWEEN;
    }

    String name();

    Operator[] operators() default {
        Operator.EQ, Operator.LT, Operator.GT, Operator.LTE, Operator.GTE, Operator.BETWEEN
    };

    String datePattern() default "yyyy-MM-dd";

    String property();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableLocalDateCompare {

        LocalDateCompare[] value();
    }

    public static class LocalDateCompareFilter implements Filter {

        private final String name;
        private final EnumSet<Operator> operators;
        private final String property;
        private final DateTimeFormatter formatter;

        public LocalDateCompareFilter(LocalDateCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.property = annotation.property();
            Filters.ensurePropertyOfAnyType(annotation, entity, property, LocalDate.class);
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
            this.formatter = DateTimeFormatter.ofPattern(annotation.datePattern());
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            final Operator operator = Operator.valueOf(values[0]);
            Filters.ensure(operators.contains(operator), "operator %s not whitelisted (%s)", operator, operators);
            final Path<LocalDate> lhs = Filters.traverseProperty(root, property);
            Filters.ensure(values.length == (operator == Operator.BETWEEN ? 3 : 2), "unexpected number of values: %d", values.length);
            final String value = values[1];
            Filters.ensure(value != null, "value cannot be null");
            final LocalDate rhs = LocalDate.parse(value, formatter);
            switch (operator) {
                case EQ:
                    return builder.equal(lhs, rhs);
                case LT:
                    return builder.lessThan(lhs, rhs);
                case GT:
                    return builder.greaterThan(lhs, rhs);
                case LTE:
                    return builder.lessThanOrEqualTo(lhs, rhs);
                case GTE:
                    return builder.greaterThanOrEqualTo(lhs, rhs);
                case BETWEEN:
                    final String value2 = values[2];
                    Filters.ensure(value2 != null, "value cannot be null");
                    final LocalDate rhs2 = LocalDate.parse(value2, formatter);
                    final LocalDate[] dates = Stream.of(rhs, rhs2).sorted().toArray((l) -> new LocalDate[l]);
                    return builder.and(builder.greaterThanOrEqualTo(lhs, dates[0]), builder.lessThanOrEqualTo(lhs, dates[1]));
                default:
                    throw new IllegalStateException("unreachable");
            }
        }

        @Override
        public String name() {
            return name;
        }

    }

}
