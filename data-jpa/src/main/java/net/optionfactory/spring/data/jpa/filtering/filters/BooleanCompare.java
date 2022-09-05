package net.optionfactory.spring.data.jpa.filtering.filters;

import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.EnumSet;
import java.util.Set;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare.BooleanCompareFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare.RepeatableBooleanCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;

/**
 * Filters a boolean property. Accepts a single parameter as truth value, which
 * should match either {@link BooleanCompare#trueValue() trueValue} or
 * {@link BooleanCompare#falseValue() falseValue}.
 */
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(BooleanCompareFilter.class)
@Repeatable(RepeatableBooleanCompare.class)
public @interface BooleanCompare {

    public enum Operator {
        EQ, NEQ;
    }

    String name();

    QueryMode mode() default QueryMode.JOIN;

    Operator[] operators() default {
        Operator.EQ, Operator.NEQ
    };

    String path();

    String trueValue() default "true";

    String falseValue() default "false";

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableBooleanCompare {

        BooleanCompare[] value();
    }

    public static class BooleanCompareFilter implements TraversalFilter<Boolean> {

        private final String name;
        private final QueryMode mode;
        private final EnumSet<Operator> operators;
        private final String trueValue;
        private final Set<String> validValues;
        private final Traversal traversal;

        public BooleanCompareFilter(BooleanCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.mode = annotation.mode();
            this.trueValue = annotation.trueValue();
            this.validValues = Set.of(annotation.trueValue(), annotation.falseValue());
            this.traversal = Filters.traversal(annotation, entity, annotation.path());
            Filters.ensurePropertyOfAnyType(annotation, entity, this.traversal, Boolean.class, boolean.class);
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
        }

        @Override
        public Predicate condition(Root<?> root, Path<Boolean> path, CriteriaBuilder builder, String[] values) {
            final Operator operator = Operator.valueOf(values[0]);
            Filters.ensure(operators.contains(operator), name, root, "operator %s not whitelisted (%s)", operator, operators);
            Filters.ensure(values.length == 2, name, root, "missing value for comparison");
            final String value = values[1];
            if (value == null) {
                return operator == Operator.EQ ? path.isNull() : path.isNotNull();
            }
            Filters.ensure(validValues.contains(value), name, root, "value does not match valid values: %s", validValues);
            return operator == Operator.EQ == trueValue.equals(value) ? builder.isTrue(path) : builder.isFalse(path);
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

        private static String str(Boolean b) {
            return b == null ? null : b.toString();
        }

        public static String[] eq(Boolean value) {
            return new String[]{Operator.EQ.name(), str(value)};
        }

        public static String[] neq(Boolean value) {
            return new String[]{Operator.NEQ.name(), str(value)};
        }
    }
}
