package net.optionfactory.spring.thymeleaf.dialects;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.function.Supplier;

/// Parses and formats money amounts, kept as `long` cents or as [BigDecimal] units, with the
/// separators of a pluggable [DecimalFormatSymbols] strategy. Meant to be exposed to templates
/// through a [net.optionfactory.spring.thymeleaf.SingletonDialect].
///
/// No currency symbol is involved, and amounts are always grouped by thousands. The strategy is
/// asked for the symbols on every call, so it can depend on the current request, and every call
/// builds its own `DecimalFormat`: an instance is thread-safe as long as the strategy is.
///
/// ```java
/// engine.addDialect(SingletonDialect.of("money", new Money(new Money.ItalianSymbols())));
/// ```
public class Money {

    private static final BigDecimal HUNDRED = new BigDecimal(100);

    private final Supplier<DecimalFormatSymbols> symbolsStrategy;

    /// @param symbolsStrategy supplies the decimal and grouping separators, on every call
    public Money(Supplier<DecimalFormatSymbols> symbolsStrategy) {
        this.symbolsStrategy = symbolsStrategy;
    }

    /// The Italian separators, `,` for decimals and `.` for grouping, as in `1.234,56`; the other
    /// symbols, such as the minus sign, are the ones of the default format locale.
    public static class ItalianSymbols implements Supplier<DecimalFormatSymbols> {

        /// @return new symbols with the Italian separators
        @Override
        public DecimalFormatSymbols get() {
            final DecimalFormatSymbols s = new DecimalFormatSymbols();
            s.setDecimalSeparator(',');
            s.setGroupingSeparator('.');
            return s;
        }
    }

    /// Parses an amount in units, such as `1.234,56`, into cents.
    ///
    /// The parsing is lenient: grouping separators are optional and not checked for position, a
    /// leading minus sign is accepted, and the parsing stops at the first character that does not
    /// belong to a number, ignoring the rest (`12abc` is 1200 cents). Decimals beyond the cents
    /// are truncated, not rounded: `1,999` is 199 cents.
    ///
    /// @param value the amount, with the separators of the strategy
    /// @return the amount in cents
    /// @throws IllegalArgumentException with a message in Italian, meant for the user, when the
    /// value does not start with a number
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

    /// @param cents the amount in cents
    /// @return the amount in units, grouped and with two decimals, such as `1.234,56`
    public String formatCents(long cents) {
        return formatCents(cents, false);
    }

    /// @param cents the amount in cents
    /// @param hideCents whether to round the amount to units, as [#format(BigDecimal, boolean)]
    /// does
    /// @return the amount in units, grouped, with two decimals or none
    public String formatCents(long cents, boolean hideCents) {
        final var bd = new BigDecimal(cents).divide(HUNDRED);
        return format(bd, hideCents);
    }

    /// @param bd the amount in units
    /// @return the amount, grouped and with two decimals, such as `1.234,56`
    public String format(BigDecimal bd) {
        return format(bd, false);
    }

    /// Formats an amount, rounding it to the shown digits with the half-even rule of
    /// `DecimalFormat`: hiding the cents, `1234,50` becomes `1.234` while `1235,50` becomes
    /// `1.236`.
    ///
    /// @param bd the amount in units
    /// @param hideCents whether to show no decimals instead of two
    /// @return the amount, grouped by thousands
    public String format(BigDecimal bd, boolean hideCents) {
        final var symbols = symbolsStrategy.get();
        return hideCents
                ? new DecimalFormat("#,###", symbols).format(bd)
                : new DecimalFormat("#,##0.00", symbols).format(bd);
    }

}
