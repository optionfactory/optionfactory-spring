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
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;

/**
 * Compares an {@link Instant} property. The first argument must be a
 * whitelisted {@link Operator}. Operators accept a single argument, while
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
        EQ, NEQ, LT, GT, LTE, GTE, BETWEEN;
    }

    public enum Format {
        ISO_8601, UNIX_S, UNIX_MS, UNIX_NS;
    }

    String name();

    Operator[] operators() default {
        Operator.EQ, Operator.NEQ, Operator.LT, Operator.GT, Operator.LTE, Operator.GTE, Operator.BETWEEN
    };

    Format format() default Format.ISO_8601;

    String path();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableInstantCompare {

        InstantCompare[] value();
    }

    public static class InstantCompareFilter implements Filter {

        private final String name;
        private final EnumSet<Operator> operators;
        private final Format format;
        private final Traversal traversal;

        public InstantCompareFilter(InstantCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.traversal = Filters.traversal(annotation, entity, annotation.path());
            Filters.ensurePropertyOfAnyType(annotation, entity, traversal, Instant.class);
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
            this.format = annotation.format();
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            final Operator operator = Operator.valueOf(values[0]);
            Filters.ensure(operators.contains(operator), name, root, "operator %s not whitelisted (%s)", operator, operators);
            final Path<Instant> lhs = Filters.path(root, traversal);
            Filters.ensure(values.length == (operator == Operator.BETWEEN ? 3 : 2), name, root, "unexpected number of values: %d", values.length);
            final String value = values[1];
            final Instant rhs = parseInstant(value);
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
                    final Instant rhs2 = parseInstant(value2);
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);                    
                    Filters.ensure(rhs2 != null, name, root, "value2 cannot be null for operator %s", operator);                    
                    final Instant[] instants = Stream.of(rhs, rhs2).sorted().toArray((l) -> new Instant[l]);
                    return builder.and(builder.greaterThanOrEqualTo(lhs, instants[0]), builder.lessThan(lhs, instants[1]));
                default:
                    throw new IllegalStateException("unreachable");
            }
        }

        private Instant parseInstant(String value) {
            if (value == null) {
                return null;
            }
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
