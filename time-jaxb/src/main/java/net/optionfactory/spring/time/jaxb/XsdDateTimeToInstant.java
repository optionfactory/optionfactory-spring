package net.optionfactory.spring.time.jaxb;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class XsdDateTimeToInstant extends XmlAdapter<String, Instant> {

    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public Instant unmarshal(String value) {
        return value == null ? null : ZonedDateTime.parse(value, FORMAT).toInstant();
    }

    @Override
    public String marshal(Instant instant) {
        return instant == null ? null : instant
                .atOffset(ZoneOffset.UTC)
                .format(FORMAT);
    }
}
