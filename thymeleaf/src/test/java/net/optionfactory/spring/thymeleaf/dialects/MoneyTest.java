package net.optionfactory.spring.thymeleaf.dialects;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MoneyTest {

    private final Money money = new Money(new Money.ItalianSymbols());

    @Test
    public void parseCentsAcceptsPlainDecimalValues() {
        Assertions.assertEquals(123456, money.parseCents("1234,56"), "units and cents are read with the decimal comma");
        Assertions.assertEquals(123400, money.parseCents("1234"), "a value without decimals is whole units");
        Assertions.assertEquals(5, money.parseCents("0,05"), "a value below one unit is read in cents");
    }

    @Test
    public void parseCentsAcceptsGroupedValues() {
        Assertions.assertEquals(123456789, money.parseCents("1.234.567,89"), "grouping dots are skipped");
    }

    @Test
    public void parseCentsAcceptsNegativeValues() {
        Assertions.assertEquals(-123456, money.parseCents("-1.234,56"), "a leading minus makes a negative amount");
    }

    @Test
    public void parseCentsRejectsInvalidValues() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> money.parseCents("not-a-number"), "a value that is not a number is rejected");
        Assertions.assertThrows(IllegalArgumentException.class, () -> money.parseCents(""), "an empty value is rejected");
    }

    @Test
    public void formatCentsRendersItalianSeparators() {
        Assertions.assertEquals("1.234,56", money.formatCents(123456), "thousands are grouped with dots, cents follow a comma");
        Assertions.assertEquals("0,05", money.formatCents(5), "a value below one unit keeps the leading zero");
        Assertions.assertEquals("1.234.567,89", money.formatCents(123456789), "every thousand is grouped");
    }

    @Test
    public void formatCentsRendersNegativeValues() {
        Assertions.assertEquals("-1.234,56", money.formatCents(-123456), "a negative amount is prefixed with a minus");
    }

    @Test
    public void formatCentsHidingCentsRoundsToNearestUnit() {
        Assertions.assertEquals("1.235", money.formatCents(123456, true), "dropping the decimals rounds 1234,56 up to 1.235");
        Assertions.assertEquals("1.234", money.formatCents(123400, true), "a whole amount loses only its zero decimals");
        Assertions.assertEquals("0", money.formatCents(0, true), "zero is rendered as a digit even without decimals");
    }

    @Test
    public void formatRendersBigDecimalWithTwoDecimals() {
        Assertions.assertEquals("1.234,50", money.format(new BigDecimal("1234.5")), "a single decimal is padded to two");
        Assertions.assertEquals("1.234,00", money.format(new BigDecimal("1234")), "a whole amount gets zero cents");
    }

    @Test
    public void separatorsComeFromTheSymbolsStrategy() {
        final var english = new Money(() -> DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        Assertions.assertEquals("1,234.56", english.formatCents(123456), "the strategy separators are used for formatting");
        Assertions.assertEquals(123456, english.parseCents("1,234.56"), "the strategy separators are used for parsing");
    }

    @Test
    public void parseCentsRejectsWhatIsNotAnAmount() {
        for (final var value : List.of("12abc", "1.2.3,4", "1,999", "12.34", "1.23,45", "-", ",")) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> money.parseCents(value), "trailing text, misplaced grouping and fractions of a cent are rejected, got through: " + value);
        }
    }

    @Test
    public void parseCentsAcceptsSurroundingSpacesAndOneDecimal() {
        Assertions.assertEquals(123450, money.parseCents(" 1.234,5 "), "surrounding spaces are ignored and one decimal is tens of cents");
    }

    @Test
    public void formatCentsHidingCentsRoundsHalfUp() {
        Assertions.assertEquals("1.235", money.formatCents(123450, true), "half a unit rounds up, 1234,50 shows as 1.235");
        Assertions.assertEquals("1.236", money.formatCents(123550, true), "half a unit rounds up, 1235,50 shows as 1.236");
        Assertions.assertEquals("-1.235", money.formatCents(-123450, true), "half a unit rounds away from zero for negative amounts");
    }
}
