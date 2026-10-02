package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.criteria.Root;
import org.springframework.util.NumberUtils;

/// Conversions of the string values a client sends to the types of the filtered properties.
public interface Values {

    /// Converts a client-provided value to the filtered property's type.
    ///
    /// Supported targets are `String` (returned as is), `char`/`Character` (a value of exactly one
    /// character), the numeric primitives (parsed with the `valueOf` of their wrapper), and the boxed
    /// numeric types, `BigInteger` and `BigDecimal` (a plain `Number` reading as a `BigDecimal`),
    /// parsed with spring's `NumberUtils.parseNumber`, which drops any whitespace and accepts the
    /// `0x` hex notation for the integral types.
    ///
    /// Every failure is reported as an [InvalidFilterRequest]: the value comes from the request, so a
    /// malformed one is a bad request, not a bug. Letting the underlying `NumberFormatException` or
    /// `StringIndexOutOfBoundsException` escape would make it indistinguishable from one.
    ///
    /// @param filterName the filter name, for the rejection
    /// @param root the query root, naming the entity in the message; may be `null`
    /// @param value the client's value, `null` being returned as `null`
    /// @param target the property type
    /// @return the converted value, boxed for a primitive target
    /// @throws InvalidFilterRequest when the value cannot be converted, or the target type is not
    /// supported
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
