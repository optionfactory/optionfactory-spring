package net.optionfactory.spring.time.jaxb;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.junit.Assert;
import org.junit.Test;

public class XsdDateTimeToInstantTest {

    private final XsdDateTimeToInstant adapter = new XsdDateTimeToInstant();

    @Test
    public void canParseDateWithOffset() {
        final Instant got = adapter.unmarshal("2003-02-01T04:05:06+01:00");
        Assert.assertEquals(OffsetDateTime.of(2003, 2, 1, 4, 5, 6, 0, ZoneOffset.ofHours(1)).toInstant(), got);
    }

    @Test(expected = DateTimeParseException.class)
    public void cannotParseDateWithOffset() {
        adapter.unmarshal("2003-02-01T04:05:06");
    }

}
