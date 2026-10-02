package net.optionfactory.spring.data.jpa.filtering.filters;

import java.time.Instant;
import java.time.LocalDate;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare.Format;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.CaseSensitivity;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.TextCompareFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FilterEncodingTest {

    private static final Instant INSTANT = Instant.parse("2020-01-02T03:04:05.123456789Z");

    @Test
    public void instantsAreEncodedInEveryFormat() {
        Assertions.assertArrayEquals(new String[]{"EQ", "2020-01-02T03:04:05.123456789Z"}, InstantCompare.Filter.INSTANCE.eq(Format.ISO_8601, INSTANT), "ISO_8601 keeps the nanoseconds");
        Assertions.assertArrayEquals(new String[]{"EQ", "1577934245"}, InstantCompare.Filter.INSTANCE.eq(Format.UNIX_S, INSTANT), "UNIX_S truncates to seconds");
        Assertions.assertArrayEquals(new String[]{"EQ", "1577934245123"}, InstantCompare.Filter.INSTANCE.eq(Format.UNIX_MS, INSTANT), "UNIX_MS truncates to milliseconds");
        Assertions.assertArrayEquals(new String[]{"EQ", "1577934245123456789"}, InstantCompare.Filter.INSTANCE.eq(Format.UNIX_NS, INSTANT), "UNIX_NS keeps the nanoseconds");
    }

    @Test
    public void instantRangesAndNullsAreEncoded() {
        Assertions.assertArrayEquals(new String[]{"BETWEEN", "1", "2"}, InstantCompare.Filter.INSTANCE.between(Format.UNIX_S, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)), "a range is the operator followed by both bounds");
        Assertions.assertArrayEquals(new String[]{"NEQ", null}, InstantCompare.Filter.INSTANCE.neq(Format.ISO_8601, null), "a null instant is encoded as null");
    }

    @Test
    public void localDatesUseTheDefaultOrTheGivenPattern() {
        final var date = LocalDate.of(2020, 1, 2);
        Assertions.assertArrayEquals(new String[]{"LT", "2020-01-02"}, LocalDateCompare.Filter.INSTANCE.lt(date), "the default pattern is yyyy-MM-dd");
        Assertions.assertArrayEquals(new String[]{"LT", "02/01/2020"}, LocalDateCompare.Filter.INSTANCE.lt("dd/MM/yyyy", date), "the given pattern is used");
        Assertions.assertArrayEquals(new String[]{"EQ", null}, LocalDateCompare.Filter.INSTANCE.eq(null), "a null date is encoded as null");
    }

    @Test
    public void numbersAreEncodedWithTheirStringForm() {
        Assertions.assertArrayEquals(new String[]{"GTE", "1.5"}, NumberCompare.Filter.INSTANCE.gte(1.5), "a number is encoded with its toString");
        Assertions.assertArrayEquals(new String[]{"EQ", null}, NumberCompare.Filter.INSTANCE.eq(null), "a null number is encoded as null");
    }

    @Test
    public void textDefaultsToCaseSensitive() {
        Assertions.assertArrayEquals(new String[]{"STARTS_WITH", "CASE_SENSITIVE", "re"}, TextCompare.Filter.INSTANCE.startsWith("re"), "the overload without a sensitivity is case sensitive");
        Assertions.assertArrayEquals(new String[]{"BETWEEN", "IGNORE_CASE", "a", "b"}, TextCompare.Filter.INSTANCE.between(CaseSensitivity.IGNORE_CASE, "a", "b"), "a range carries both bounds after the sensitivity");
    }

    @Test
    public void booleansEncodeNullAsNull() {
        Assertions.assertArrayEquals(new String[]{"EQ", null}, BooleanCompare.Filter.INSTANCE.eq((Boolean) null), "a null truth value is encoded as null");
        Assertions.assertArrayEquals(new String[]{"EQ", "yes"}, BooleanCompare.Filter.INSTANCE.eq("yes"), "a custom truth value is passed as is");
    }

    @Test
    public void likeWildcardsAndTheEscapeCharacterAreEscaped() {
        Assertions.assertEquals("50\\%\\_off\\\\", TextCompareFilter.escapeForLike("50%_off\\"), "%, _ and the escape character are each prefixed by a backslash");
        Assertions.assertEquals("plain", TextCompareFilter.escapeForLike("plain"), "text without wildcards is unchanged");
    }
}
