package net.optionfactory.spring.marshaling.jaxb.time;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class XsdDateTimeToLocalDateTimeTest {

    private final XsdDateTimeToLocalDateTime adapter = new XsdDateTimeToLocalDateTime();

    @Test
    public void canParseDateWithoutOffset() {
        final LocalDateTime got = adapter.unmarshal("2003-02-01T04:05:06");
        Assertions.assertEquals(LocalDateTime.of(2003, 2, 1, 4, 5, 6, 0), got);
    }

    @Test
    public void cannotParseDateWithOffset() {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            adapter.unmarshal("2003-02-01T04:05:06+01:00");
        });
        
    }

    
    @XmlRootElement(name = "B")
    public static class BeanWithLocalDateTime {

        @XmlJavaTypeAdapter(XsdDateTimeToLocalDateTime.class)
        public LocalDateTime at;
    }

    @Test
    public void canMarshalNotNull() throws JAXBException {
        final BeanWithLocalDateTime b = new BeanWithLocalDateTime();
        b.at = LocalDateTime.of(2020, Month.MARCH, 2, 4, 5, 6);
        final String got = Marshalling.marshal(b);
        final String expected = "<at>2020-03-02T04:05:06</at>";
        Assertions.assertTrue(got.contains(expected), String.format("expected to contain: %s, got: %s", expected, got));

    }

    @Test
    public void canUnmarshalNotNull() throws JAXBException {
        BeanWithLocalDateTime b = Marshalling.unmarshal("<B><at>2020-03-02T04:05:06</at></B>", BeanWithLocalDateTime.class);
        Assertions.assertEquals(LocalDateTime.of(2020, Month.MARCH, 2, 4, 5, 6), b.at);
    }    
 
    @Test
    public void canMarshalNull() throws JAXBException {
        final BeanWithLocalDateTime b = new BeanWithLocalDateTime();
        final String got = Marshalling.marshal(b);
        final String expected = "<B/>";
        Assertions.assertTrue(got.contains(expected), String.format("expected to contain: %s, got: %s", expected, got));
    }

    @Test
    public void canUnmarshalNull() throws JAXBException {
        BeanWithLocalDateTime b1 = Marshalling.unmarshal("<B/>", BeanWithLocalDateTime.class);
        Assertions.assertEquals(null, b1.at);
        BeanWithLocalDateTime b2 = Marshalling.unmarshal("<B><at/></B>", BeanWithLocalDateTime.class);
        Assertions.assertEquals(null, b2.at);
        BeanWithLocalDateTime b3 = Marshalling.unmarshal("<B><at xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/></B>", BeanWithLocalDateTime.class);
        Assertions.assertEquals(null, b3.at);
    }    
    
}
