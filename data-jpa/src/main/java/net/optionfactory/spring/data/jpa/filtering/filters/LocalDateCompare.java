package net.optionfactory.spring.data.jpa.filtering.filters;

import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;
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
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare.LocalDateCompareFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare.RepeatableLocalDateCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;

/**
 * Compares a {@link LocalDate} path.The first argument must be a whitelisted
 * {@link Operator}. The {@link Operator#BETWEEN} accepts two arguments, while
 * the other operators accept a single argument, which format must match the
 * configured {@link #datePattern() datePattern}.
 */
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(LocalDateCompareFilter.class)
@Repeatable(RepeatableLocalDateCompare.class)
public @interface LocalDateCompare {

    public enum Operator {
        EQ, NEQ, LT, GT, LTE, GTE, BETWEEN;
    }

    String name();

    QueryMode mode() default QueryMode.JOIN;

    Operator[] operators() default {
        Operator.EQ, Operator.NEQ, Operator.LT, Operator.GT, Operator.LTE, Operator.GTE, Operator.BETWEEN
    };

    String datePattern() default "yyyy-MM-dd";

    String path();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableLocalDateCompare {

        LocalDateCompare[] value();
    }

    public static class LocalDateCompareFilter implements TraversalFilter<LocalDate> {

        private final String name;
        private final QueryMode mode;
        private final EnumSet<Operator> operators;
        private final DateTimeFormatter formatter;
        private final Traversal traversal;

        public LocalDateCompareFilter(LocalDateCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.mode = annotation.mode();
            this.traversal = Filters.traversal(annotation, entity, annotation.path());
            Filters.ensurePropertyOfAnyType(annotation, entity, traversal, LocalDate.class);
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
            this.formatter = DateTimeFormatter.ofPattern(annotation.datePattern());
        }

        @Override
        public Predicate condition(Root<?> root, Path<LocalDate> lhs, CriteriaBuilder builder, String[] values) {
            final Operator operator = Operator.valueOf(values[0]);
            Filters.ensure(operators.contains(operator), name, root, "operator %s not whitelisted (%s)", operator, operators);
            Filters.ensure(values.length == (operator == Operator.BETWEEN ? 3 : 2), name, root, "unexpected number of values: %d", values.length);
            final String value = values[1];
            final LocalDate rhs = value == null ? null : LocalDate.parse(value, formatter);
            switch (operator) {
                case EQ:
                    return rhs == null ? lhs.isNull() : builder.equal(lhs, rhs);
                case NEQ:
                    return rhs == null ? lhs.isNotNull() : builder.or(lhs.isNull(), builder.notEqual(lhs, rhs));
                case LT:
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    return builder.lessThan(lhs, rhs);
                case GT:
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    return builder.greaterThan(lhs, rhs);
                case LTE:
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    return builder.lessThanOrEqualTo(lhs, rhs);
                case GTE:
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    return builder.greaterThanOrEqualTo(lhs, rhs);
                case BETWEEN:
                    final String value2 = values[2];
                    final LocalDate rhs2 = value == null ? null : LocalDate.parse(value2, formatter);
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    Filters.ensure(rhs2 != null, name, root, "value cannot be null for operator %s", operator);
                    final LocalDate[] sorted = Stream.of(rhs, rhs2).sorted().toArray((l) -> new LocalDate[l]);
                    return builder.and(builder.greaterThanOrEqualTo(lhs, sorted[0]), builder.lessThanOrEqualTo(lhs, sorted[1]));
                default:
                    throw new IllegalStateException("unreachable");
            }
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

}
