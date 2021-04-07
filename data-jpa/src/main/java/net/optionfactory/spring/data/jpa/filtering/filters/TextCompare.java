package net.optionfactory.spring.data.jpa.filtering.filters;

import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.TextCompareFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.RepeatableTextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;

/**
 * Compares a text property. The three arguments must be a whitelisted
 * {@link Operator}, a whitelisted {@link Mode} and the comparison value.
 */
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(TextCompareFilter.class)
@Repeatable(RepeatableTextCompare.class)
public @interface TextCompare {

    public enum Operator {
        EQ, NEQ, LT, GT, LTE, GTE, BETWEEN, CONTAINS, STARTS_WITH, ENDS_WITH;
    }

    public enum Mode {
        CASE_SENSITIVE, IGNORE_CASE;
    }

    String name();

    Operator[] operators() default {
        Operator.EQ, Operator.NEQ, Operator.LT, Operator.GT, Operator.LTE, Operator.GTE, Operator.BETWEEN, Operator.CONTAINS, Operator.STARTS_WITH, Operator.ENDS_WITH
    };

    Mode[] modes() default {
        Mode.CASE_SENSITIVE, Mode.IGNORE_CASE
    };

    String path();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableTextCompare {

        TextCompare[] value();
    }

    public static class TextCompareFilter implements Filter {

        private final String name;
        private final EnumSet<Operator> operators;
        private final EnumSet<Mode> modes;
        private final Traversal traversal;

        public TextCompareFilter(TextCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
            this.modes = EnumSet.of(annotation.modes()[0], annotation.modes());
            this.traversal = Filters.traversal(annotation, entity, annotation.path());
            Filters.ensurePropertyOfAnyType(annotation, entity, traversal, String.class);
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            Filters.ensure(values.length == 3, name, root, "expected operator,mode,value got %s", List.of(values));
            final Operator operator = Operator.valueOf(values[0]);
            Filters.ensure(operators.contains(operator), name, root, "operator %s not whitelisted (%s)", operator, operators);
            final Mode mode = Mode.valueOf(values[1]);
            Filters.ensure(modes.contains(mode), name, root, "mode %s not whitelisted (%s)", mode, modes);
            final String value = values[2];
            Filters.ensure(value != null, name, root, "value cannot be null");
            final Expression<String> lhs = mode == Mode.CASE_SENSITIVE ? Filters.path(root, traversal) : builder.lower(Filters.path(root, traversal));
            final String rhs = mode == Mode.CASE_SENSITIVE || value == null ? value : value.toLowerCase();
            switch (operator) {
                case EQ:
                    return rhs == null ? lhs.isNull() : builder.equal(lhs, rhs);
                case NEQ:
                    return rhs == null ? lhs.isNotNull() : builder.notEqual(lhs, rhs);
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
                    final String value2 = values[3];
                    final String rhs2 = mode == Mode.CASE_SENSITIVE || value2 == null ? null : value2.toLowerCase();
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);                    
                    Filters.ensure(rhs2 != null, name, root, "value2 cannot be null for operator %s", operator);                    
                    final String[] sorted = Stream.of(rhs, rhs2).sorted().toArray((l) -> new String[l]);
                    return builder.and(builder.greaterThanOrEqualTo(lhs, sorted[0]), builder.lessThan(lhs, sorted[1]));
                case CONTAINS:
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);                    
                    return builder.like(lhs, "%" + rhs + "%");
                case STARTS_WITH:
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);                    
                    return builder.like(lhs, rhs + "%");
                case ENDS_WITH:
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);                    
                    return builder.like(lhs, "%" + rhs);
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
