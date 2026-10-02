package net.optionfactory.spring.marshaling.jaxb.time;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/// Adapts an `xs:date` to a [LocalDate].
///
/// Unmarshalling accepts the optional time zone offset `xs:date` allows (`2003-02-01+01:00`) and
/// discards it; marshalling writes the date alone.
///
/// A malformed value makes [#unmarshal] throw a `DateTimeParseException`. Under the default
/// unmarshaller event handler of the glassfish JAXB runtime that exception is only reported as a
/// validation event, and the property is left `null`: set a `ValidationEventHandler` that fails on
/// errors to reject malformed documents. An empty element also unmarshals to `null`.
public class XsdDateToLocalDate extends XmlAdapter<String, LocalDate> {

    /// The ISO date format, with an optional offset when parsing.
    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_DATE;

    /// @param value the lexical `xs:date`, with or without offset
    /// @return the date, or `null` for a `null` value
    /// @throws java.time.format.DateTimeParseException when the value is malformed
    @Override
    public LocalDate unmarshal(String value) {
        return value == null ? null : LocalDate.parse(value, FORMAT);
    }

    /// @param v the date to write
    /// @return the date as `yyyy-MM-dd`, or `null` for a `null` value, which omits the element
    @Override
    public String marshal(LocalDate v) {
        return v == null ? null : FORMAT.format(v);
    }
}
