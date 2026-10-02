package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ValuesTest {

    @Test
    public void nullIsConvertedToNull() {
        Assertions.assertNull(Values.convert("f", null, null, Integer.class), "a null value stays null whatever the target");
    }

    @Test
    public void stringsArePassedThrough() {
        Assertions.assertEquals(" as is ", Values.convert("f", null, " as is ", String.class), "a string target receives the value untouched");
    }

    @Test
    public void charactersRequireExactlyOneCharacter() {
        Assertions.assertEquals('x', Values.convert("f", null, "x", char.class), "a single character converts to a char");
        Assertions.assertEquals('x', Values.convert("f", null, "x", Character.class), "a single character converts to a Character");
        final var thrown = Assertions.assertThrows(InvalidFilterRequest.class, () -> Values.convert("f", null, "xy", char.class), "more than one character is rejected");
        Assertions.assertEquals("expected a single character, got 'xy'", thrown.reason, "the reason quotes the value");
    }

    @Test
    public void primitivesAreConvertedToTheirBoxes() {
        Assertions.assertEquals((byte) 1, Values.convert("f", null, "1", byte.class), "a byte target yields a Byte");
        Assertions.assertEquals((short) 2, Values.convert("f", null, "2", short.class), "a short target yields a Short");
        Assertions.assertEquals(3, Values.convert("f", null, "3", int.class), "an int target yields an Integer");
        Assertions.assertEquals(4L, Values.convert("f", null, "4", long.class), "a long target yields a Long");
        Assertions.assertEquals(5.5f, Values.convert("f", null, "5.5", float.class), "a float target yields a Float");
        Assertions.assertEquals(6.5d, Values.convert("f", null, "6.5", double.class), "a double target yields a Double");
    }

    @Test
    public void numbersAreParsedToTheirOwnType() {
        Assertions.assertEquals(new BigDecimal("1.10"), Values.convert("f", null, "1.10", BigDecimal.class), "a BigDecimal keeps its scale");
        Assertions.assertEquals(new BigInteger("12345678901234567890"), Values.convert("f", null, "12345678901234567890", BigInteger.class), "a BigInteger is not limited to a long");
        Assertions.assertEquals(new BigDecimal("7"), Values.convert("f", null, "7", Number.class), "a plain Number reads as a BigDecimal");
        Assertions.assertEquals(255, Values.convert("f", null, "0xFF", Integer.class), "boxed integral types accept the hex notation");
    }

    @Test
    public void aValueThatDoesNotFitTheTypeIsARejectedRequest() {
        final var thrown = Assertions.assertThrows(InvalidFilterRequest.class, () -> Values.convert("byWeight", null, "300", byte.class), "an out-of-range byte is rejected");
        Assertions.assertEquals("byWeight", thrown.filter, "the rejection names the filter");
        Assertions.assertTrue(thrown.getMessage().startsWith("in filter byWeight@?:"), "with no query root the entity reads as ?, got: " + thrown.getMessage());
    }

    @Test
    public void malformedNumbersAreRejectedRequestsForBoxedTypesToo() {
        Assertions.assertThrows(InvalidFilterRequest.class, () -> Values.convert("f", null, "ten", Long.class), "a non numeric boxed long is rejected");
        Assertions.assertThrows(InvalidFilterRequest.class, () -> Values.convert("f", null, "1.5", BigInteger.class), "a decimal BigInteger is rejected");
    }

    @Test
    public void unsupportedTargetsAreRejectedRequests() {
        Assertions.assertThrows(InvalidFilterRequest.class, () -> Values.convert("f", null, "1", AtomicLong.class), "a Number type NumberUtils cannot parse is rejected");
        Assertions.assertThrows(InvalidFilterRequest.class, () -> Values.convert("f", null, "1", Object.class), "a non numeric, non textual target is rejected");
    }
}
