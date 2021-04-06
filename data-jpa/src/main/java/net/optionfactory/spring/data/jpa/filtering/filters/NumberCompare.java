package net.optionfactory.spring.data.jpa.filtering.filters;

import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;
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
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare.NumberCompareFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare.RepeatableNumberCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Values;

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

    public static class NumberCompareFilter implements Filter {

        private final String name;
        private final EnumSet<Operator> operators;
        private final Class<? extends Number> propertyClass;
        private final Traversal traversal;

        public NumberCompareFilter(NumberCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.traversal = Filters.traversal(annotation, entity, annotation.path());
            this.propertyClass = (Class<? extends Number>) Filters.ensurePropertyOfAnyType(annotation, entity, traversal, Number.class, byte.class, short.class, int.class, long.class, float.class, double.class, char.class);
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            final Operator operator = Operator.valueOf(values[0]);
            Filters.ensure(operators.contains(operator), name, root, "operator %s not whitelisted (%s)", operator, operators);
            final String value = values[1];
            Filters.ensure(value != null || operator == Operator.EQ, name, root, "value cannot be null when operator is not %s", Operator.EQ.name());
            final Path<Number> lhs = Filters.path(root, traversal);
            final Number rhs = (Number) Values.convert(name, root, value, propertyClass);
            switch (operator) {
                case EQ:
                    return rhs == null ? lhs.isNull() : builder.equal(lhs, rhs);
                case NEQ:
                    return rhs == null ? lhs.isNotNull() : builder.notEqual(lhs, rhs);
                case LT:
                    return builder.lt(lhs, rhs);
                case GT:
                    return builder.gt(lhs, rhs);
                case LTE:
                    return builder.le(lhs, rhs);
                case GTE:
                    return builder.ge(lhs, rhs);
                case BETWEEN:
                    final String value2 = values[2];
                    Filters.ensure(value2 != null, name, root, "value2 cannot be null");
                    final Number rhs2 = (Number) Values.convert(name, root, value2, propertyClass);
                    final Number[] numbers = Stream.of(rhs, rhs2).sorted().toArray((l) -> new Number[l]);
                    return builder.and(builder.ge(lhs, numbers[0]), builder.lt(lhs, numbers[1]));
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
