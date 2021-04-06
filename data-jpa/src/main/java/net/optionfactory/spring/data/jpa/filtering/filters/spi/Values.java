package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import javax.persistence.criteria.Root;
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
            return NumberUtils.parseNumber(value, (Class<? extends Number>) target);
        }
        if (byte.class.isAssignableFrom(target)) {
            return Byte.parseByte(value);
        }
        if (short.class.isAssignableFrom(target)) {
            return Short.parseShort(value);
        }
        if (int.class.isAssignableFrom(target)) {
            return Integer.parseInt(value);
        }
        if (long.class.isAssignableFrom(target)) {
            return Long.parseLong(value);
        }
        if (float.class.isAssignableFrom(target)) {
            return Float.parseFloat(value);
        }
        if (double.class.isAssignableFrom(target)) {
            return Double.parseDouble(value);
        }
        if (char.class.isAssignableFrom(target)) {
            return value.charAt(0);
        }
        throw new InvalidFilterRequest(filterName, root, String.format("Unconvertible value '%s' to %s", value, target.getSimpleName()));
    }
}
