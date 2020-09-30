package net.optionfactory.spring.time.jaxb;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.junit.Assert;
import org.junit.Test;

public class XsdDateToLocalDateTest {

    private final XsdDateToLocalDate adapter = new XsdDateToLocalDate();

    @Test
    public void canParseDateWithoutOffset() {
        final LocalDate got = adapter.unmarshal("2003-02-01");
        Assert.assertEquals(LocalDate.of(2003, 2, 1), got);
    }

    @Test(expected = DateTimeParseException.class)
    public void cannotParseDateWithOffset() {
        adapter.unmarshal("2003-02-01+01:00");
    }

}
