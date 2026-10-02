package net.optionfactory.spring.thymeleaf.dialects;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
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
    /// The whole value, surrounding spaces aside, must be an amount: an optional leading minus
    /// sign, the units either without grouping or grouped by thousands with the grouping separator
    /// in the right places, and optionally the decimal separator followed by one or two digits.
    /// Trailing text (`12abc`), misplaced grouping (`1.2.3`) and fractions of a cent (`1,999`) are
    /// rejected rather than ignored or truncated, and so are amounts beyond a `long` of cents.
    ///
    /// @param value the amount, with the separators of the strategy
    /// @return the amount in cents
    /// @throws IllegalArgumentException with a message in Italian, meant for the user, when the
    /// value is not such an amount
    public long parseCents(String value) {
        final var symbols = symbolsStrategy.get();
        final var grouping = Pattern.quote(String.valueOf(symbols.getGroupingSeparator()));
        final var decimal = Pattern.quote(String.valueOf(symbols.getDecimalSeparator()));
        final var minus = Pattern.quote(String.valueOf(symbols.getMinusSign()));
        final var amount = Pattern.compile("(" + minus + ")?(\\d+|\\d{1,3}(" + grouping + "\\d{3})+)(" + decimal + "(\\d{1,2}))?");
        final var matcher = amount.matcher(value == null ? "" : value.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Specificare una cifra valida (es. 1234,56)");
        }
        final var units = matcher.group(2).replaceAll(grouping, "");
        final var cents = matcher.group(5) == null ? "" : matcher.group(5);
        try {
            final var parsed = new BigDecimal(units + (cents.isEmpty() ? "" : "." + cents)).movePointRight(2).longValueExact();
            return matcher.group(1) == null ? parsed : -parsed;
        } catch (ArithmeticException ex) {
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

    /// Formats an amount, rounding it to the shown digits half-up, away from zero on a tie: hiding
    /// the cents, `1234,50` becomes `1.235` and `-1234,50` becomes `-1.235`.
    ///
    /// @param bd the amount in units
    /// @param hideCents whether to show no decimals instead of two
    /// @return the amount, grouped by thousands
    public String format(BigDecimal bd, boolean hideCents) {
        final var symbols = symbolsStrategy.get();
        final var format = hideCents
                ? new DecimalFormat("#,###", symbols)
                : new DecimalFormat("#,##0.00", symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(bd);
    }

}
