package net.optionfactory.spring.marshaling.jaxb.time;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/// Adapts an `xs:dateTime` carrying a time zone offset to an [OffsetDateTime], keeping the offset
/// both ways.
///
/// A local `xs:dateTime`, without offset, is rejected: use [XsdDateTimeToLocalDateTime] for
/// those.
///
/// A malformed value makes [#unmarshal] throw a `DateTimeParseException`. Under the default
/// unmarshaller event handler of the glassfish JAXB runtime that exception is only reported as a
/// validation event, and the property is left `null`: set a `ValidationEventHandler` that fails on
/// errors to reject malformed documents. An empty element also unmarshals to `null`.
public class XsdDateTimeToOffsetDateTime extends XmlAdapter<String, OffsetDateTime> {

    /// The ISO offset date-time format, used both ways.
    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /// @param value the lexical `xs:dateTime`, with an offset
    /// @return the date-time with its offset, or `null` for a `null` value
    /// @throws java.time.format.DateTimeParseException when the value is malformed or has no offset
    @Override
    public OffsetDateTime unmarshal(String value) {
        return value == null ? null : OffsetDateTime.parse(value, FORMAT);
    }

    /// @param v the date-time to write
    /// @return the date-time with its offset (`Z` for UTC), or `null` for a `null` value, which
    /// omits the element
    @Override
    public String marshal(OffsetDateTime v) {
        return v == null ? null : FORMAT.format(v);
    }
}
