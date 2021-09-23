package net.optionfactory.spring.data.jpa.filtering.filters;


import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Values;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Stream;

@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WhitelistedFilter(NumberWithBetweenCompare.NumberWithBetweenCompareFilter.class)
@Repeatable(NumberWithBetweenCompare.RepeatableNumberCompare.class)
public @interface NumberWithBetweenCompare {

    String name();

    Operator[] operators() default {
            Operator.LT, Operator.LTE, Operator.EQ, Operator.GTE, Operator.GT, Operator.BETWEEN, Operator.BETWEEN_INCLUSIVE
    };

    String property();

    public static class NumberWithBetweenCompareFilter implements Filter {
        private final String name;
        private final EnumSet<Operator> operators;
        private final Class<? extends Number> propertyClass;
        private final String property;

        public NumberWithBetweenCompareFilter(NumberWithBetweenCompare nc, EntityType<?> entity) {
            this.name = nc.name();
            this.property = nc.property();
            this.propertyClass =
                    (Class<? extends Number>) Filters.ensurePropertyOfAnyType(nc,
                                                                              entity,
                                                                              property,
                                                                              Number.class,
                                                                              Optional.class,
                                                                              byte.class,
                                                                              short.class,
                                                                              int.class,
                                                                              long.class,
                                                                              float.class,
                                                                              double.class,
                                                                              char.class);
            this.operators = EnumSet.of(nc.operators()[0], nc.operators());
        }

        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            final Operator operator = Operator.valueOf(values[0]);
            Filters.ensure(this.operators.contains(operator),
                           "operator %s not whitelisted (%s)",
                           new Object[]{operator, this.operators});
            Filters.ensure(values.length == (operator == Operator.BETWEEN || operator == Operator.BETWEEN_INCLUSIVE
                                   ? 3
                                   : 2),
                           "unexpected number of values: %d",
                           values.length);
            final String value = values[1];
            Filters.ensure(value != null || operator == Operator.EQ,
                           "value cannot be null when operator is not %s",
                           new Object[]{Operator.EQ.name()});
            final Path<Number> lhs = Filters.traverseProperty(root, this.property);
            final Number rhs = (Number) Values.convert(value, propertyClass);
            switch (operator) {
                case LT:
                    return builder.lt(lhs, rhs);
                case LTE:
                    return builder.le(lhs, rhs);
                case EQ:
                    return rhs == null ? lhs.isNull() : builder.equal(lhs, rhs);
                case GTE:
                    return builder.ge(lhs, rhs);
                case GT:
                    return builder.gt(lhs, rhs);
                case BETWEEN:
                case BETWEEN_INCLUSIVE:
                    final String value2 = values[2];
                    final Number rhs2 = (Number) Values.convert(value2, propertyClass);
                    Filters.ensure(value2 != null, "value cannot be null");
                    final Number[] numbers = Stream.of(rhs, rhs2)
                                                   .sorted()
                                                   .toArray((l) -> new Number[l]);
                    if (operator == Operator.BETWEEN) {
                        return builder.and(builder.gt(lhs, numbers[0]), builder.lt(lhs, numbers[1]));
                    } else if (operator == Operator.BETWEEN_INCLUSIVE) {
                        return builder.and(builder.ge(lhs, numbers[0]), builder.le(lhs, numbers[1]));
                    }
                default:
                    throw new IllegalStateException("unreachable");
            }
        }

        public String name() {
            return this.name;
        }
    }

    @Documented
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RepeatableNumberCompare {
        NumberWithBetweenCompare[] value();
    }

    public static enum Operator {
        LT,
        LTE,
        EQ,
        GTE,
        GT,
        BETWEEN,
        BETWEEN_INCLUSIVE;

        private Operator() {
        }
    }
}
