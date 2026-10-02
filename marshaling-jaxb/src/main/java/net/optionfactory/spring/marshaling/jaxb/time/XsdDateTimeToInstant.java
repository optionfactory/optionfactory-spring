package net.optionfactory.spring.marshaling.jaxb.time;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/// Adapts an `xs:dateTime` carrying a time zone offset to an [Instant].
///
/// Unmarshalling requires the offset (`Z` or `±hh:mm`) and converts the instant to UTC, so the
/// original offset is lost; a local `xs:dateTime`, without offset, is rejected, since it does not
/// identify an instant. Marshalling always writes UTC, with a `Z` suffix and the fractional seconds
/// only when non-zero (e.g. `1970-01-01T00:00:00.5Z`).
///
/// A malformed value makes [#unmarshal] throw a `DateTimeParseException`. Under the default
/// unmarshaller event handler of the glassfish JAXB runtime that exception is only reported as a
/// validation event, and the property is left `null`: set a `ValidationEventHandler` that fails on
/// errors to reject malformed documents. An empty element also unmarshals to `null`.
///
/// ```java
/// @XmlJavaTypeAdapter(XsdDateTimeToInstant.class)
/// public Instant at;
/// ```
public class XsdDateTimeToInstant extends XmlAdapter<String, Instant> {

    /// The ISO offset date-time format, used both ways.
    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /// @param value the lexical `xs:dateTime`, with an offset
    /// @return the instant, or `null` for a `null` value
    /// @throws java.time.format.DateTimeParseException when the value is malformed or has no offset
    @Override
    public Instant unmarshal(String value) {
        return value == null ? null : ZonedDateTime.parse(value, FORMAT).toInstant();
    }

    /// @param instant the instant to write
    /// @return the instant as a UTC `xs:dateTime`, or `null` for a `null` instant, which omits the
    /// element
    @Override
    public String marshal(Instant instant) {
        return instant == null ? null : instant
                .atOffset(ZoneOffset.UTC)
                .format(FORMAT);
    }
}
