package net.optionfactory.spring.marshaling.jaxb.time;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class XsdDateToLocalDate extends XmlAdapter<String, LocalDate> {

    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_DATE;

    @Override
    public LocalDate unmarshal(String value) {
        return value == null ? null : LocalDate.parse(value, FORMAT);
    }

    @Override
    public String marshal(LocalDate v) {
        return v == null ? null : FORMAT.format(v);
    }
}
