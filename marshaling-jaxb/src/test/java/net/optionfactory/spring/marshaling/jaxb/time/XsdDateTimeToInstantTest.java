package net.optionfactory.spring.marshaling.jaxb.time;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.UnmarshalException;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class XsdDateTimeToInstantTest {

    private final XsdDateTimeToInstant adapter = new XsdDateTimeToInstant();

    @Test
    public void canParseDateWithOffset() {
        final Instant got = adapter.unmarshal("2003-02-01T04:05:06+01:00");
        Assertions.assertEquals(OffsetDateTime.of(2003, 2, 1, 4, 5, 6, 0, ZoneOffset.ofHours(1)).toInstant(), got, "the offset identifies the instant");
    }

    @Test
    public void cannotParseDateWithoutOffset() {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            adapter.unmarshal("2003-02-01T04:05:06");
        }, "a local date-time does not identify an instant");
    }

    @Test
    public void marshalsInUtcWhateverTheParsedOffset() {
        final var parsed = adapter.unmarshal("2003-02-01T04:05:06+01:00");
        Assertions.assertEquals("2003-02-01T03:05:06Z", adapter.marshal(parsed), "the instant is written in UTC, the original offset is lost");
    }

    @Test
    public void marshalsFractionalSecondsOnlyWhenPresent() {
        Assertions.assertEquals("1970-01-01T00:00:00.5Z", adapter.marshal(Instant.ofEpochMilli(500)), "non-zero fractional seconds are written");
        Assertions.assertEquals("1970-01-01T00:00:00Z", adapter.marshal(Instant.EPOCH), "zero fractional seconds are omitted");
    }

    @XmlRootElement(name = "B")
    public static class BeanWithInstant {

        @XmlJavaTypeAdapter(XsdDateTimeToInstant.class)
        public Instant at;
    }

    @Test
    public void canMarshalNotNull() throws JAXBException {
        final BeanWithInstant b = new BeanWithInstant();
        b.at = Instant.EPOCH;
        final String got = Marshalling.marshal(b);
        final String expected = "<at>1970-01-01T00:00:00Z</at>";
        Assertions.assertTrue(got.contains(expected), String.format("expected to contain: %s, got: %s", expected, got));
    }

    @Test
    public void canUnmarshalNotNull() throws JAXBException {
        BeanWithInstant b = Marshalling.unmarshal("<B><at>1970-01-01T00:00:00Z</at></B>", BeanWithInstant.class);
        Assertions.assertEquals(Instant.EPOCH, b.at, "a UTC xs:dateTime unmarshals to its instant");
    }

    @Test
    public void canMarshalNull() throws JAXBException {
        final BeanWithInstant b = new BeanWithInstant();
        b.at = null;
        final String got = Marshalling.marshal(b);
        final String expected = "<B/>";
        Assertions.assertTrue(got.contains(expected), String.format("expected to contain: %s, got: %s", expected, got));
    }

    @Test
    public void canUnmarshalNull() throws JAXBException {
        BeanWithInstant b1 = Marshalling.unmarshal("<B/>", BeanWithInstant.class);
        Assertions.assertEquals(null, b1.at, "a missing element unmarshals to null");
        BeanWithInstant b2 = Marshalling.unmarshal("<B><at/></B>", BeanWithInstant.class);
        Assertions.assertEquals(null, b2.at, "an empty element unmarshals to null");
        BeanWithInstant b3 = Marshalling.unmarshal("<B><at xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/></B>", BeanWithInstant.class);
        Assertions.assertEquals(null, b3.at, "a nil element unmarshals to null");
    }

    @Test
    public void malformedValuesUnmarshalToNullUnderTheDefaultEventHandler() throws JAXBException {
        final var b = Marshalling.unmarshal("<B><at>garbage</at></B>", BeanWithInstant.class);
        Assertions.assertNull(b.at, "the default event handler reports the parse failure and leaves the property unset");
    }

    @Test
    public void malformedValuesAreRejectedUnderAFailingEventHandler() {
        Assertions.assertThrows(UnmarshalException.class, () -> {
            Marshalling.unmarshalStrictly("<B><at>garbage</at></B>", BeanWithInstant.class);
        }, "an event handler failing on errors turns the parse failure into an unmarshal failure");
    }

}
