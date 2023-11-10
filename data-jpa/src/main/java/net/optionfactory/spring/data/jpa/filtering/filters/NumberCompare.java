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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare.NumberCompareFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare.RepeatableNumberCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Values;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;

/**
 * Compares a numeric path, either a primitive type (not {@code boolean}) or a
 * {@link Number} (such as boxed primitives, {@link BigInteger} or
 * {@link BigDecimal}). The first argument must be a whitelisted
 * {@link Operator}. All operators accept a single numeric argument, that must
 * be convertible to the relative path type.
 */
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(NumberCompareFilter.class)
@Repeatable(RepeatableNumberCompare.class)
public @interface NumberCompare {

    public enum Operator {
        EQ, NEQ, LT, GT, LTE, GTE, BETWEEN;
    }

    String name();

    QueryMode mode() default QueryMode.JOIN;

    Operator[] operators() default {
        Operator.EQ, Operator.NEQ, Operator.LT, Operator.GT, Operator.LTE, Operator.GTE, Operator.BETWEEN
    };

    String path();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableNumberCompare {

        NumberCompare[] value();
    }

    public static class NumberCompareFilter implements TraversalFilter<Number> {

        private final String name;
        private final QueryMode mode;
        private final EnumSet<Operator> operators;
        private final Class<? extends Number> propertyClass;
        private final Traversal traversal;

        public NumberCompareFilter(NumberCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.mode = annotation.mode();
            this.traversal = Filters.traversal(annotation, entity, annotation.path());
            this.propertyClass = (Class<? extends Number>) Filters.ensurePropertyOfAnyType(annotation, entity, traversal, Number.class, byte.class, short.class, int.class, long.class, float.class, double.class, char.class);
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
        }

        @Override
        public Predicate condition(Root<?> root, Path<Number> lhs, CriteriaBuilder builder, String[] values) {
            final Operator operator = Operator.valueOf(values[0]);
            Filters.ensure(operators.contains(operator), name, root, "operator %s not whitelisted (%s)", operator, operators);
            final String value = values[1];
            final Number rhs = (Number) Values.convert(name, root, value, propertyClass);
            switch (operator) {
                case EQ:
                    return rhs == null ? lhs.isNull() : builder.equal(lhs, rhs);
                case NEQ:
                    return rhs == null ? lhs.isNotNull() : builder.or(lhs.isNull(), builder.notEqual(lhs, rhs));
                case LT:
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    return builder.lt(lhs, rhs);
                case GT:
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    return builder.gt(lhs, rhs);
                case LTE:
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    return builder.le(lhs, rhs);
                case GTE:
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    return builder.ge(lhs, rhs);
                case BETWEEN:
                    final String value2 = values[2];
                    final Number rhs2 = (Number) Values.convert(name, root, value2, propertyClass);
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    Filters.ensure(rhs2 != null, name, root, "value2 cannot be null for operator %s", operator);
                    final Number[] sorted = Stream.of(rhs, rhs2).sorted().toArray((l) -> new Number[l]);
                    return builder.and(builder.ge(lhs, sorted[0]), builder.lt(lhs, sorted[1]));
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

    public static class Filter {

        private static String str(Number n) {
            return n == null ? null : n.toString();
        }

        public static String[] eq(Number value) {
            return new String[]{Operator.EQ.name(), str(value)};
        }

        public static String[] neq(Number value) {
            return new String[]{Operator.NEQ.name(), str(value)};
        }

        public static String[] lt(Number value) {
            return new String[]{Operator.LT.name(), str(value)};
        }

        public static String[] gt(Number value) {
            return new String[]{Operator.GT.name(), str(value)};
        }

        public static String[] lte(Number value) {
            return new String[]{Operator.LTE.name(), str(value)};
        }

        public static String[] gte(Number value) {
            return new String[]{Operator.GTE.name(), str(value)};
        }

        public static String[] between(Number value1, Number value2) {
            return new String[]{Operator.BETWEEN.name(), str(value1), str(value2)};
        }

    }

}
