package net.optionfactory.spring.thymeleaf.dialects;

import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MoneyTest {

    private final Money money = new Money(new Money.ItalianSymbols());

    @Test
    public void parseCentsAcceptsPlainDecimalValues() {
        Assertions.assertEquals(123456, money.parseCents("1234,56"));
        Assertions.assertEquals(123400, money.parseCents("1234"));
        Assertions.assertEquals(5, money.parseCents("0,05"));
    }

    @Test
    public void parseCentsAcceptsGroupedValues() {
        Assertions.assertEquals(123456789, money.parseCents("1.234.567,89"));
    }

    @Test
    public void parseCentsRejectsInvalidValues() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> money.parseCents("not-a-number"));
    }

    @Test
    public void formatCentsRendersItalianSeparators() {
        Assertions.assertEquals("1.234,56", money.formatCents(123456));
        Assertions.assertEquals("0,05", money.formatCents(5));
        Assertions.assertEquals("1.234.567,89", money.formatCents(123456789));
    }

    @Test
    public void formatCentsHidingCentsRoundsToNearestUnit() {
        //DecimalFormat rounds when dropping decimals: 1234.56 renders as 1.235
        Assertions.assertEquals("1.235", money.formatCents(123456, true));
        Assertions.assertEquals("1.234", money.formatCents(123400, true));
    }

    @Test
    public void formatRendersBigDecimalWithTwoDecimals() {
        Assertions.assertEquals("1.234,50", money.format(new BigDecimal("1234.5")));
        Assertions.assertEquals("1.234,00", money.format(new BigDecimal("1234")));
    }
}
