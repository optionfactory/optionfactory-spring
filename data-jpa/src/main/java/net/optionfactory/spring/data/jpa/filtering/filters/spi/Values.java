package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.criteria.Root;
import org.springframework.util.NumberUtils;

public interface Values {

    /**
     * Converts a client-provided value to the filtered property's type.
     *
     * Every failure is reported as an {@link InvalidFilterRequest}: the value comes from the
     * request, so a malformed one is a bad request, not a bug. Letting the underlying
     * {@code NumberFormatException} or {@code StringIndexOutOfBoundsException} escape would make
     * it indistinguishable from one.
     */
    public static Object convert(String filterName, Root<?> root, String value, Class<?> target) {
        if (value == null) {
            return null;
        }
        if (String.class.isAssignableFrom(target)) {
            return value;
        }
        if (char.class.isAssignableFrom(target) || Character.class.isAssignableFrom(target)) {
            if (value.length() != 1) {
                throw new InvalidFilterRequest(filterName, root, String.format("expected a single character, got '%s'", value));
            }
            return value.charAt(0);
        }
        try {
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
        } catch (IllegalArgumentException ex) {
            throw new InvalidFilterRequest(filterName, root, String.format("cannot convert value '%s' to %s: %s", value, target.getSimpleName(), ex.getMessage()));
        }
        throw new InvalidFilterRequest(filterName, root, String.format("Unconvertible value '%s' to %s", value, target.getSimpleName()));
    }
}
