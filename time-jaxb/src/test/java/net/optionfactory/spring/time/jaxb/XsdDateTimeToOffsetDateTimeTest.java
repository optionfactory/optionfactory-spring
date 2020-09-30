package net.optionfactory.spring.time.jaxb;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.junit.Assert;
import org.junit.Test;

public class XsdDateTimeToOffsetDateTimeTest {

    private final XsdDateTimeToOffsetDateTime adapter = new XsdDateTimeToOffsetDateTime();

    @Test
    public void canParseDateWithOffset() {
        final OffsetDateTime got = adapter.unmarshal("2003-02-01T04:05:06+01:00");
        Assert.assertEquals(OffsetDateTime.of(2003, 2, 1, 4, 5, 6, 0, ZoneOffset.ofHours(1)), got);
    }

    @Test(expected = DateTimeParseException.class)
    public void cannotParseDateWithoutOffset() {
        adapter.unmarshal("2003-02-01T04:05:06");
    }

}
