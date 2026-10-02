package net.optionfactory.spring.marshaling.jaxb.time;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class XsdDateTimeToOffsetDateTimeTest {

    private final XsdDateTimeToOffsetDateTime adapter = new XsdDateTimeToOffsetDateTime();

    @Test
    public void canParseDateWithOffset() {
        final OffsetDateTime got = adapter.unmarshal("2003-02-01T04:05:06+01:00");
        Assertions.assertEquals(OffsetDateTime.of(2003, 2, 1, 4, 5, 6, 0, ZoneOffset.ofHours(1)), got, "the offset is kept");
    }

    @Test
    public void cannotParseDateWithoutOffset() {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            adapter.unmarshal("2003-02-01T04:05:06");
        }, "a local date-time has no offset to keep");
    }

    @Test
    public void marshalsTheOriginalOffset() {
        final var value = OffsetDateTime.of(2003, 2, 1, 4, 5, 6, 0, ZoneOffset.ofHours(-3));
        Assertions.assertEquals("2003-02-01T04:05:06-03:00", adapter.marshal(value), "the offset is written as is, not converted to UTC");
    }

    @XmlRootElement(name = "B")
    public static class BeanWithOffsetDateTime {

        @XmlJavaTypeAdapter(XsdDateTimeToOffsetDateTime.class)
        public OffsetDateTime at;
    }

    @Test
    public void canMarshalNotNull() throws JAXBException {
        final BeanWithOffsetDateTime b = new BeanWithOffsetDateTime();
        b.at = OffsetDateTime.of(2020, 2, 1, 20, 19, 18, 0, ZoneOffset.UTC);
        final String got = Marshalling.marshal(b);
        final String expected = "<at>2020-02-01T20:19:18Z</at>";
        Assertions.assertTrue(got.contains(expected), String.format("expected to contain: %s, got: %s", expected, got));
    }

    @Test
    public void canUnmarshalNotNull() throws JAXBException {
        BeanWithOffsetDateTime b = Marshalling.unmarshal("<B><at>2020-02-01T20:19:18Z</at></B>", BeanWithOffsetDateTime.class);
        Assertions.assertEquals(OffsetDateTime.of(2020, 2, 1, 20, 19, 18, 0, ZoneOffset.UTC), b.at, "a Z suffix unmarshals to the UTC offset");
    }

    @Test
    public void canMarshalNull() throws JAXBException {
        final BeanWithOffsetDateTime b = new BeanWithOffsetDateTime();
        final String got = Marshalling.marshal(b);
        final String expected = "<B/>";
        Assertions.assertTrue(got.contains(expected), String.format("expected to contain: %s, got: %s", expected, got));
    }

    @Test
    public void canUnmarshalNull() throws JAXBException {
        BeanWithOffsetDateTime b1 = Marshalling.unmarshal("<B/>", BeanWithOffsetDateTime.class);
        Assertions.assertEquals(null, b1.at, "a missing element unmarshals to null");
        BeanWithOffsetDateTime b2 = Marshalling.unmarshal("<B><at/></B>", BeanWithOffsetDateTime.class);
        Assertions.assertEquals(null, b2.at, "an empty element unmarshals to null");
        BeanWithOffsetDateTime b3 = Marshalling.unmarshal("<B><at xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/></B>", BeanWithOffsetDateTime.class);
        Assertions.assertEquals(null, b3.at, "a nil element unmarshals to null");
    }

}
