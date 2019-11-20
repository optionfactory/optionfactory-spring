package net.optionfactory.data.jpa.filtering.filters;

import net.optionfactory.data.jpa.filtering.Filter;
import net.optionfactory.data.jpa.filtering.filters.spi.WhitelistedFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.data.jpa.filtering.filters.BooleanCompare.BooleanCompareFilter;
import net.optionfactory.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.data.jpa.filtering.filters.BooleanCompare.RepeatableBooleanCompare;

@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(filter=BooleanCompareFilter.class)
@Repeatable(RepeatableBooleanCompare.class)
public @interface BooleanCompare {

    String name();

    String property();

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
        private final String property;
        private final String trueValue;
        private final String falseValue;
        private final boolean ignoreCase;

        public BooleanCompareFilter(BooleanCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.property = annotation.property();
            this.trueValue = annotation.trueValue();
            this.falseValue = annotation.falseValue();
            this.ignoreCase = annotation.ignoreCase();
            Filters.ensurePropertyOfAnyType(annotation, entity, property, Boolean.class, boolean.class);
        }

        @Override
        public Predicate toPredicate(CriteriaBuilder builder, Root<?> root, String[] values) {
            Filters.ensure(values.length == 1, "missing value for comparison");
            final String value = values[0];
            final Path<Boolean> p = Filters.traverseProperty(root, property);
            if (value == null) {
                return p.isNull();
            }
            if ((ignoreCase && value.equalsIgnoreCase(trueValue)) || value.equals(trueValue)) {
                return builder.isTrue(p);
            }
            if ((ignoreCase && value.equalsIgnoreCase(falseValue)) || value.equals(falseValue)) {
                return builder.isFalse(p);
            }
            throw new Filters.InvalidFilterRequest(String.format("unexpected boolean value '%s', expecting either '%s' or '%s'", value, trueValue, falseValue));
        }

        @Override
        public String name() {
            return name;
        }
    }
}
