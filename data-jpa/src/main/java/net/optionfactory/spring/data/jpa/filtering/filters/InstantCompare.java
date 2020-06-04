package net.optionfactory.spring.data.jpa.filtering.filters;

import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigInteger;
import java.time.Instant;
import java.util.EnumSet;
import java.util.stream.Stream;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare.InstantCompareFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare.RepeatableInstantCompare;

/**
 * Compares an {@link Instant} property. The first argument must be a
 * whitelisted {@link Operator}. Operators {@link Operator#FROM} (inclusive) and
 * {@link Operator#BEFORE} (exclusive) accept a single argument, while
 * {@link Operator#BETWEEN} (left inclusive and right exclusive) accepts a
 * range.
 */
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(InstantCompareFilter.class)
@Repeatable(RepeatableInstantCompare.class)
public @interface InstantCompare {

    public enum Operator {
        BEFORE, FROM, BETWEEN;
    }

    public enum Format {
        ISO_8601, UNIX_S, UNIX_MS, UNIX_NS;
    }

    String name();

    Operator[] operators() default {
        Operator.BEFORE, Operator.FROM, Operator.BETWEEN
    };

    Format format() default Format.ISO_8601;

    String property();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableInstantCompare {

        InstantCompare[] value();
    }

    public static class InstantCompareFilter implements Filter {

        private final String name;
        private final EnumSet<Operator> operators;
        private final String property;
        private final Format format;

        public InstantCompareFilter(InstantCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.property = annotation.property();
            Filters.ensurePropertyOfAnyType(annotation, entity, property, Instant.class);
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
            this.format = annotation.format();
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            final Operator operator = Operator.valueOf(values[0]);
            Filters.ensure(operators.contains(operator), "operator %s not whitelisted (%s)", operator, operators);
            final Path<Instant> lhs = Filters.traverseProperty(root, property);
            Filters.ensure(values.length == (operator == Operator.BETWEEN ? 3 : 2), "unexpected number of values: %d", values.length);
            final String value = values[1];
            Filters.ensure(value != null, "value cannot be null");
            final Instant rhs = parseInstant(value);
            switch (operator) {
                case BEFORE:
                    return builder.lessThan(lhs, rhs);
                case FROM:
                    return builder.greaterThanOrEqualTo(lhs, rhs);
                case BETWEEN:
                    final String value2 = values[2];
                    Filters.ensure(value2 != null, "value cannot be null");
                    final Instant rhs2 = parseInstant(value2);
                    final Instant[] instants = Stream.of(rhs, rhs2).sorted().toArray((l) -> new Instant[l]);
                    return builder.and(builder.greaterThanOrEqualTo(lhs, instants[0]), builder.lessThan(lhs, instants[1]));
                default:
                    throw new IllegalStateException("unreachable");
            }
        }

        private Instant parseInstant(String value) {
            switch (format) {
                case ISO_8601:
                    return Instant.parse(value);
                case UNIX_S:
                    return Instant.ofEpochSecond(Long.parseLong(value, 10));
                case UNIX_MS:
                    return Instant.ofEpochMilli(Long.parseLong(value, 10));
                case UNIX_NS:
                    final BigInteger nanoseconds = new BigInteger(value, 10);
                    final BigInteger[] secondsAndNanosecondsFraction = nanoseconds.divideAndRemainder(BigInteger.valueOf(1_000_000_000L));
                    return Instant.ofEpochSecond(secondsAndNanosecondsFraction[0].longValueExact(), secondsAndNanosecondsFraction[1].longValueExact());
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
