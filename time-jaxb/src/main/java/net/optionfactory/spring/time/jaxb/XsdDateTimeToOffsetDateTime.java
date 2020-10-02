package net.optionfactory.spring.time.jaxb;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class XsdDateTimeToOffsetDateTime extends XmlAdapter<String, OffsetDateTime> {

    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public OffsetDateTime unmarshal(String value) {
        return value == null ? null : OffsetDateTime.parse(value, FORMAT);
    }

    @Override
    public String marshal(OffsetDateTime v) {
        return v == null ? null : FORMAT.format(v);
    }
}
