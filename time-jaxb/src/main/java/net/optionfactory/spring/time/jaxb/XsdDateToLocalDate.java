package net.optionfactory.spring.time.jaxb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class XsdDateToLocalDate extends XmlAdapter<String, LocalDate> {

    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public LocalDate unmarshal(String value) {
        return value == null ? null : LocalDate.parse(value, FORMAT);
    }

    @Override
    public String marshal(LocalDate v) {
        return v == null ? null : FORMAT.format(v);
    }
}
