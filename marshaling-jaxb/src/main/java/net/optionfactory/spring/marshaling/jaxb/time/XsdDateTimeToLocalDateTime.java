package net.optionfactory.spring.marshaling.jaxb.time;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/// Adapts a local `xs:dateTime`, without time zone offset, to a [LocalDateTime].
///
/// A value carrying an offset is rejected rather than silently dropping it: use
/// [XsdDateTimeToOffsetDateTime] or [XsdDateTimeToInstant] for those. Marshalling always writes the
/// seconds (`2020-01-01T00:00:00`), and the fractional seconds only when non-zero.
///
/// A malformed value makes [#unmarshal] throw a `DateTimeParseException`. Under the default
/// unmarshaller event handler of the glassfish JAXB runtime that exception is only reported as a
/// validation event, and the property is left `null`: set a `ValidationEventHandler` that fails on
/// errors to reject malformed documents. An empty element also unmarshals to `null`.
public class XsdDateTimeToLocalDateTime extends XmlAdapter<String, LocalDateTime> {

    /// The ISO local date-time format, used both ways.
    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /// @param value the lexical `xs:dateTime`, without offset
    /// @return the local date-time, or `null` for a `null` value
    /// @throws java.time.format.DateTimeParseException when the value is malformed or carries an
    /// offset
    @Override
    public LocalDateTime unmarshal(String value) {
        return value == null ? null : LocalDateTime.parse(value, FORMAT);
    }

    /// @param v the date-time to write
    /// @return the local date-time, or `null` for a `null` value, which omits the element
    @Override
    public String marshal(LocalDateTime v) {
        return v == null ? null : FORMAT.format(v);
    }
}
