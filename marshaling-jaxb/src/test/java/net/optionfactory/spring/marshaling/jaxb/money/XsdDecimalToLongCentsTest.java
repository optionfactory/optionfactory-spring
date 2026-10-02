package net.optionfactory.spring.marshaling.jaxb.money;

import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class XsdDecimalToLongCentsTest {

    private final XsdDecimalToLongCents adapter = new XsdDecimalToLongCents();

    public static Stream<Arguments> unmarshalData() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("0", 0L),
                Arguments.of("1", 100L),
                Arguments.of("0.1", 10L),
                Arguments.of("0.01", 1L),
                Arguments.of("0.001", 0L),
                Arguments.of("0.019", 1L),
                Arguments.of("123", 12300L),
                Arguments.of("123.4", 12340L),
                Arguments.of("123.40", 12340L),
                Arguments.of("123.04", 12304L),
                Arguments.of("12345.06", 1234506L),
                Arguments.of("-1.5", -150L),
                Arguments.of("-0.019", -1L)
        );
    }

    @ParameterizedTest
    @MethodSource("unmarshalData")
    public void unmarshal(String input, Long expected) {
        Assertions.assertEquals(expected, adapter.unmarshal(input), "decimal digits past the cents are truncated toward zero");
    }

    public static Stream<Arguments> marshalData() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(0L, "0"),
                Arguments.of(1L, "0.01"),
                Arguments.of(10L, "0.1"),
                Arguments.of(100L, "1"),
                Arguments.of(12300L, "123"),
                Arguments.of(12340L, "123.4"),
                Arguments.of(12304L, "123.04"),
                Arguments.of(1234506L, "12345.06"),
                Arguments.of(-150L, "-1.5"),
                Arguments.of(123456789L, "1234567.89")
        );
    }

    @ParameterizedTest
    @MethodSource("marshalData")
    public void marshal(Long input, String expected) {
        Assertions.assertEquals(expected, adapter.marshal(input), "the shortest decimal form, with '.' as separator and no grouping");
    }

    @Test
    public void rejectsValuesNotStartingWithANumber() {
        final var ex = Assertions.assertThrows(IllegalArgumentException.class, () -> adapter.unmarshal("abc"), "a value without a number is unparseable");
        Assertions.assertTrue(ex.getMessage().contains("abc"), "the failure names the offending value");
    }

    @Test
    public void rejectsAnEmptyValue() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> adapter.unmarshal(""), "an empty value is unparseable");
    }

    @Test
    public void marshalingIsTheInverseOfUnmarshaling() {
        for (final var cents : new long[]{0, 1, 99, 100, -1, -12345, Long.MAX_VALUE, Long.MIN_VALUE}) {
            Assertions.assertEquals(cents, adapter.unmarshal(adapter.marshal(cents)), "cents survive a round trip");
        }
    }

}
