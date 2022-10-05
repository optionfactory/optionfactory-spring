package net.optionfactory.spring.thymeleaf.dialects;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.function.Supplier;

public class Money {

    private static final BigDecimal HUNDRED = new BigDecimal(100);

    private final Supplier<DecimalFormatSymbols> symbolsStrategy;

    public Money(Supplier<DecimalFormatSymbols> symbolsStrategy) {
        this.symbolsStrategy = symbolsStrategy;
    }

    public static class ItalianSymbols implements Supplier<DecimalFormatSymbols> {

        @Override
        public DecimalFormatSymbols get() {
            final DecimalFormatSymbols s = new DecimalFormatSymbols();
            s.setDecimalSeparator(',');
            s.setGroupingSeparator('.');
            return s;
        }
    }

    public long parseCents(String value) {
        final var decimalFormat = new DecimalFormat("#,##0.##", symbolsStrategy.get());
        decimalFormat.setParseBigDecimal(true);

        try {
            final BigDecimal euros = (BigDecimal) decimalFormat.parse(value);
            return euros.multiply(new BigDecimal(100)).longValue();
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Specificare una cifra valida (es. 1234,56)");
        }
    }

    public String formatCents(long cents) {
        return formatCents(cents, false);
    }

    public String formatCents(long cents, boolean hideCents) {
        final var bd = new BigDecimal(cents).divide(HUNDRED);
        return format(bd, hideCents);
    }

    public String format(BigDecimal bd) {
        return format(bd, false);
    }

    public String format(BigDecimal bd, boolean hideCents) {
        final var symbols = symbolsStrategy.get();
        return hideCents
                ? new DecimalFormat("#,###", symbols).format(bd)
                : new DecimalFormat("#,##0.00", symbols).format(bd);
    }

}
