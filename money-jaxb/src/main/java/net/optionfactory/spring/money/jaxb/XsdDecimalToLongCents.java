package net.optionfactory.spring.money.jaxb;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class XsdDecimalToLongCents extends XmlAdapter<String, Long> {

    private static final DecimalFormatSymbols XSD_DECIMAL_SYMBOLS = new DecimalFormatSymbols();

    static {
        XSD_DECIMAL_SYMBOLS.setDecimalSeparator('.');
    }

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

    @Override
    public String marshal(Long cents) {
        if (cents == null) {
            return null;
        }
        final var bd = new BigDecimal(cents).movePointLeft(2);
        return new DecimalFormat("0.##", XSD_DECIMAL_SYMBOLS).format(bd);
    }

}
