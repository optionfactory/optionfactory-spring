package net.optionfactory.spring.time.jaxb;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class XsdDateTimeToLocalDateTime extends XmlAdapter<String, LocalDateTime> {

    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public LocalDateTime unmarshal(String value) {
        return value == null ? null : LocalDateTime.parse(value, FORMAT);
    }

    @Override
    public String marshal(LocalDateTime v) {
        return v == null ? null : FORMAT.format(v);
    }
}
