package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.criteria.Root;
import org.springframework.util.NumberUtils;

public interface Values {

    public static Object convert(String filterName, Root<?> root, String value, Class<?> target) {
        if (value == null) {
            return null;
        }
        if (String.class.isAssignableFrom(target)) {
            return value;
        }
        if (Number.class.isAssignableFrom(target)) {
            @SuppressWarnings("unchecked")
            final var a = NumberUtils.parseNumber(value, (Class<? extends Number>) target);
            return a;
        }
        if (byte.class.isAssignableFrom(target)) {
            return Byte.valueOf(value);
        }
        if (short.class.isAssignableFrom(target)) {
            return Short.valueOf(value);
        }
        if (int.class.isAssignableFrom(target)) {
            return Integer.valueOf(value);
        }
        if (long.class.isAssignableFrom(target)) {
            return Long.valueOf(value);
        }
        if (float.class.isAssignableFrom(target)) {
            return Float.valueOf(value);
        }
        if (double.class.isAssignableFrom(target)) {
            return Double.valueOf(value);
        }
        if (char.class.isAssignableFrom(target)) {
            return value.charAt(0);
        }
        throw new InvalidFilterRequest(filterName, root, String.format("Unconvertible value '%s' to %s", value, target.getSimpleName()));
    }
}
