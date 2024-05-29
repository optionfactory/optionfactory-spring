package net.optionfactory.spring.pem.der;

import java.time.Instant;
import net.optionfactory.spring.pem.der.DerCursor.DerValue;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class DateParsingTest {

    public static record D(String message, Instant expected, String parseable) {

    }

    @DataPoints
    public static D[] ds = new D[]{
        new D("can parse with seconds, with Z", Instant.parse("2023-12-13T01:45:50Z"), "231213014550Z"),
        new D("can parse without seconds, with Z", Instant.parse("2023-12-13T01:45:00Z"), "2312130145Z"),
        new D("can parse with seconds, with offset", Instant.parse("2023-12-13T01:45:50+01:20"), "231213014550+0120"),
        new D("can parse without seconds, with offset", Instant.parse("2023-12-13T01:45:00+01:20"), "2312130145+0120")
    };

    @Theory
    public void canParseUtcTime(D d) {
        Assert.assertEquals(d.message(), d.expected(), Instant.from(DerValue.UTC_TIME_PATTERN.parse(d.parseable())));

    }

    @Test
    public void canUnmarshalDerUtcValue() {
        byte[] bytes = new byte[]{0x17, 0x0D, 0x31, 0x36, 0x30, 0x33, 0x31, 0x37, 0x31, 0x36, 0x34, 0x30, 0x34, 0x36, 0x5A};
        Instant got = DerCursor.flat(bytes)
                .next().utc(bytes);

        Assert.assertEquals(
                Instant.parse("2016-03-17T16:40:46Z"),
                got
        );

    }
}
