package net.optionfactory.spring.marshaling.jaxb.time;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.time.LocalDate;
import java.time.Month;
import org.junit.Assert;
import org.junit.Test;

public class XsdDateToLocalDateTest {

    private final XsdDateToLocalDate adapter = new XsdDateToLocalDate();

    @Test
    public void canParseDateWithoutOffset() {
        final LocalDate got = adapter.unmarshal("2003-02-01");
        Assert.assertEquals(LocalDate.of(2003, 2, 1), got);
    }

    @Test
    public void canParseDateWithOffset() {
        final var got = adapter.unmarshal("2003-02-01+01:00");
        Assert.assertEquals(LocalDate.of(2003, 2, 1), got);
    }

    @XmlRootElement(name = "B")
    public static class BeanWithLocalDate {

        @XmlJavaTypeAdapter(XsdDateToLocalDate.class)
        public LocalDate at;
    }

    @Test
    public void canMarshalNotNull() throws JAXBException {
        final BeanWithLocalDate b = new BeanWithLocalDate();
        b.at = LocalDate.of(2020, Month.MARCH, 2);
        final String got = Marshalling.marshal(b);
        final String expected = "<at>2020-03-02</at>";
        Assert.assertTrue(String.format("expected to contain: %s, got: %s", expected, got), got.contains(expected));
    }

    @Test
    public void canUnmarshalNotNull() throws JAXBException {
        BeanWithLocalDate b = Marshalling.unmarshal("<B><at>2020-03-02</at></B>", BeanWithLocalDate.class);
        Assert.assertEquals(LocalDate.of(2020, Month.MARCH, 2), b.at);
    }

    @Test
    public void canMarshalNull() throws JAXBException {
        final BeanWithLocalDate b = new BeanWithLocalDate();
        final String got = Marshalling.marshal(b);
        final String expected = "<B/>";
        Assert.assertTrue(String.format("expected to contain: %s, got: %s", expected, got), got.contains(expected));
    }

    @Test
    public void canUnmarshalNull() throws JAXBException {
        BeanWithLocalDate b1 = Marshalling.unmarshal("<B/>", BeanWithLocalDate.class);
        Assert.assertEquals(null, b1.at);
        BeanWithLocalDate b2 = Marshalling.unmarshal("<B><at/></B>", BeanWithLocalDate.class);
        Assert.assertEquals(null, b2.at);
        BeanWithLocalDate b3 = Marshalling.unmarshal("<B><at xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/></B>", BeanWithLocalDate.class);
        Assert.assertEquals(null, b3.at);
    }

}
