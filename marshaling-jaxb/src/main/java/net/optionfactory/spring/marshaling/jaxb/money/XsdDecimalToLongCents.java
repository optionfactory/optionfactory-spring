package net.optionfactory.spring.marshaling.jaxb.money;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/// Adapts an `xs:decimal` amount of currency units to a `Long` number of cents (hundredths).
///
/// Unmarshalling keeps the first two decimal digits and drops the rest, truncating toward zero
/// rather than rounding (`0.019` is `1`, `-0.001` is `0`). Marshalling writes the shortest form,
/// with `.` as decimal separator and no trailing zeros or grouping (`12340` is `123.4`, `100` is
/// `1`).
///
/// Parsing goes through a `DecimalFormat`, so it is more lenient than `xs:decimal` in some ways and
/// stricter in others: a leading `+` or surrounding whitespace is rejected, while only the longest
/// parseable prefix of the value is read and the rest ignored (`12abc` is `1200`, `1,000` is `100`),
/// and an exponent is accepted (`1E2` is `10000`). An amount whose cents do not fit a `long`
/// overflows silently.
///
/// ```java
/// @XmlJavaTypeAdapter(XsdDecimalToLongCents.class)
/// public Long amount;
/// ```
public class XsdDecimalToLongCents extends XmlAdapter<String, Long> {

    private static final Pattern XSD_DECIMAL = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)");
    private static final DecimalFormatSymbols XSD_DECIMAL_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);

    static {
        XSD_DECIMAL_SYMBOLS.setDecimalSeparator('.');
    }

    /// Reads an amount in the `xs:decimal` lexical form, `[+-]?(\d+(\.\d*)?|\.\d+)` once the
    /// surrounding whitespace is collapsed, into cents.
    ///
    /// The whole value must be an amount: trailing text, grouping separators and exponents are
    /// rejected, and so are fractions of a cent (`0.019`) rather than truncated, and amounts beyond
    /// a `long` of cents rather than overflowed. Trailing zeros past the cents are accepted:
    /// `0.010` is 1 cent.
    ///
    /// @param value the lexical decimal amount
    /// @return the amount in cents, or `null` for a `null` value
    /// @throws IllegalArgumentException when the value is not an `xs:decimal` amount in whole cents
    @Override
    public Long unmarshal(String value) {
        if (value == null) {
            return null;
        }
        final var trimmed = value.strip();
        if (!XSD_DECIMAL.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(String.format("Unparseable decimal value: %s", value));
        }
        try {
            return new BigDecimal(trimmed.endsWith(".") ? trimmed + "0" : trimmed).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(String.format("Decimal value is not a whole amount of cents within range: %s", value), ex);
        }
    }

    /// @param cents the amount in cents
    /// @return the decimal amount, or `null` for `null` cents
    @Override
    public String marshal(Long cents) {
        if (cents == null) {
            return null;
        }
        final var bd = new BigDecimal(cents).movePointLeft(2);
        return new DecimalFormat("0.##", XSD_DECIMAL_SYMBOLS).format(bd);
    }

}
