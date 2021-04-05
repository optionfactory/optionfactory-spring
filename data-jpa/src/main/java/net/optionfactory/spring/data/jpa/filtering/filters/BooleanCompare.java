package net.optionfactory.spring.data.jpa.filtering.filters;

import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;

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

    String name();

    String path();

    String trueValue() default "true";

    String falseValue() default "false";

    boolean ignoreCase() default true;

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableBooleanCompare {

        BooleanCompare[] value();
    }

    public static class BooleanCompareFilter implements Filter {

        private final String name;
        private final String trueValue;
        private final String falseValue;
        private final boolean ignoreCase;
        private final Traversal traversal;

        public BooleanCompareFilter(BooleanCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.trueValue = annotation.trueValue();
            this.falseValue = annotation.falseValue();
            this.ignoreCase = annotation.ignoreCase();
            this.traversal = Filters.traversal(annotation, entity, annotation.path());
            Filters.ensurePropertyOfAnyType(annotation, entity, this.traversal, Boolean.class, boolean.class);
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            Filters.ensure(values.length == 1, "missing value for comparison");
            final String value = values[0];
            final Path<Boolean> p = Filters.path(root, traversal);
            if (value == null) {
                return p.isNull();
            }
            if ((ignoreCase && value.equalsIgnoreCase(trueValue)) || value.equals(trueValue)) {
                return builder.isTrue(p);
            }
            if ((ignoreCase && value.equalsIgnoreCase(falseValue)) || value.equals(falseValue)) {
                return builder.isFalse(p);
            }
            throw new InvalidFilterRequest(String.format("unexpected boolean value '%s', expecting either '%s' or '%s'", value, trueValue, falseValue));
        }

        @Override
        public String name() {
            return name;
        }
    }
}
