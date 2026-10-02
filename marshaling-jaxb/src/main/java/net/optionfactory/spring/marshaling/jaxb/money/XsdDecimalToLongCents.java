package net.optionfactory.spring.marshaling.jaxb.money;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
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

    private static final DecimalFormatSymbols XSD_DECIMAL_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);

    static {
        XSD_DECIMAL_SYMBOLS.setDecimalSeparator('.');
    }

    /// @param value the lexical decimal amount
    /// @return the amount in cents, or `null` for a `null` value
    /// @throws IllegalArgumentException when no number can be read from the start of the value
    @Override
    public Long unmarshal(String value) {
        if (value == null) {
            return null;
        }

        final var decimalFormat = new DecimalFormat("0.##", XSD_DECIMAL_SYMBOLS);
        decimalFormat.setParseBigDecimal(true);
        try {
            final BigDecimal parsed = (BigDecimal) decimalFormat.parse(value);
            return parsed.movePointRight(2).longValue();
        } catch (ParseException ex) {
            throw new IllegalArgumentException(String.format("Unparseable decimal value: %s", value));
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
