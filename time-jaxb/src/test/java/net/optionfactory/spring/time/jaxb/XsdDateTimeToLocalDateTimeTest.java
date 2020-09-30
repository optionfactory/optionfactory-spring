package net.optionfactory.spring.time.jaxb;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import org.junit.Assert;
import org.junit.Test;

public class XsdDateTimeToLocalDateTimeTest {

    private final XsdDateTimeToLocalDateTime adapter = new XsdDateTimeToLocalDateTime();

    @Test
    public void canParseDateWithoutOffset() {
        final LocalDateTime got = adapter.unmarshal("2003-02-01T04:05:06");
        Assert.assertEquals(LocalDateTime.of(2003, 2, 1, 4, 5, 6, 0), got);
    }

    @Test(expected = DateTimeParseException.class)
    public void cannotParseDateWithOffset() {
        adapter.unmarshal("2003-02-01T04:05:06+01:00");
    }

}
