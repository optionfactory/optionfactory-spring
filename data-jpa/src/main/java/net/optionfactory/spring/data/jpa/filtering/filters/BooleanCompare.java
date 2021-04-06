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
import java.util.Set;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
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

    public static class BooleanCompareFilter implements Filter {

        private final String name;
        private final EnumSet<Operator> operators;
        private final String trueValue;
        private final Set<String> validValues;
        private final Traversal traversal;

        public BooleanCompareFilter(BooleanCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.trueValue = annotation.trueValue();
            this.validValues = Set.of(annotation.trueValue(), annotation.falseValue());
            this.traversal = Filters.traversal(annotation, entity, annotation.path());
            Filters.ensurePropertyOfAnyType(annotation, entity, this.traversal, Boolean.class, boolean.class);
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            final Operator operator = Operator.valueOf(values[0]);
            Filters.ensure(operators.contains(operator), name, root, "operator %s not whitelisted (%s)", operator, operators);
            Filters.ensure(values.length == 2, name, root, "missing value for comparison");
            final String value = values[1];
            final Path<Boolean> p = Filters.path(root, traversal);
            if (value == null) {
                return operator == Operator.EQ ? p.isNull() : p.isNotNull();
            }
            Filters.ensure(validValues.contains(value), name, root, "value does not match valid values: %s", validValues);
            return operator == Operator.EQ == trueValue.equals(value) ? builder.isTrue(p) : builder.isFalse(p);
        }

        @Override
        public String name() {
            return name;
        }

    }
}
