package net.optionfactory.data.jpa.filtering.filters;

import net.optionfactory.data.jpa.filtering.filters.spi.WhitelistedFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;
import java.util.stream.Stream;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.data.jpa.filtering.filters.InList.InListFilter;
import net.optionfactory.data.jpa.filtering.filters.InList.RepeatableInList;
import net.optionfactory.data.jpa.filtering.filters.spi.Filters;
import org.springframework.util.NumberUtils;

@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(InListFilter.class)
@Repeatable(RepeatableInList.class)
public @interface InList {

    String name();

    String property();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableInList {

        InList[] value();
    }

    public static class InListFilter implements Filter {

        private final String name;
        private final String property;
        private final Class<?> propertyClass;

        public InListFilter(InList annotation, EntityType<?> entityType) {
            this.name = annotation.name();
            this.property = annotation.property();
            this.propertyClass = entityType.getAttribute(annotation.property()).getJavaType();
            Filters.ensurePropertyOfAnyType(annotation, entityType, property, String.class, Number.class, byte.class, short.class, int.class, long.class, float.class, double.class, char.class);
        }

        @Override
        public Predicate toPredicate(CriteriaBuilder builder, Root<?> root, String[] values) {
            if (values.length == 0) {
                return builder.disjunction();
            }
            final Path<?> p = Filters.traverseProperty(root, property);
            final Object[] nonNullValues = Stream.of(values).filter(Objects::nonNull).map(this::convert).toArray();
            final boolean hasNullValues = nonNullValues.length < values.length;
            if (hasNullValues) {
                return builder.or(p.isNull(), p.in(nonNullValues));
            }
            return p.in(nonNullValues);
        }

        private Object convert(String value) {
            if (Number.class.isAssignableFrom(propertyClass)) {
                return NumberUtils.parseNumber(value, (Class<? extends Number>) propertyClass);
            }
            if (byte.class.isAssignableFrom(propertyClass)) {
                return Byte.parseByte(value);
            }
            if (short.class.isAssignableFrom(propertyClass)) {
                return Short.parseShort(value);
            }
            if (int.class.isAssignableFrom(propertyClass)) {
                return Integer.parseInt(value);
            }
            if (long.class.isAssignableFrom(propertyClass)) {
                return Long.parseLong(value);
            }
            if (float.class.isAssignableFrom(propertyClass)) {
                return Float.parseFloat(value);
            }
            if (double.class.isAssignableFrom(propertyClass)) {
                return Double.parseDouble(value);
            }
            if (char.class.isAssignableFrom(propertyClass)) {
                return value.charAt(0);
            }
            return value;
        }

        @Override
        public String name() {
            return name;
        }
    }
}
