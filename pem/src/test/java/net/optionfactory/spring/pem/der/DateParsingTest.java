package net.optionfactory.spring.pem.der;

import java.time.Instant;
import java.util.stream.Stream;
import net.optionfactory.spring.pem.der.DerCursor.DerValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DateParsingTest {

    public static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of("can parse with seconds, with Z", Instant.parse("2023-12-13T01:45:50Z"), "231213014550Z"),
                Arguments.of("can parse without seconds, with Z", Instant.parse("2023-12-13T01:45:00Z"), "2312130145Z"),
                Arguments.of("can parse with seconds, with offset", Instant.parse("2023-12-13T01:45:50+01:20"), "231213014550+0120"),
                Arguments.of("can parse without seconds, with offset", Instant.parse("2023-12-13T01:45:00+01:20"), "2312130145+0120"),
                Arguments.of("years 50 to 99 are in the twentieth century, as in X.509", Instant.parse("1950-01-01T00:00:00Z"), "500101000000Z"),
                Arguments.of("years 00 to 49 are in the twenty-first century, as in X.509", Instant.parse("2049-12-31T23:59:59Z"), "491231235959Z")
        );
    }

    @ParameterizedTest
    @MethodSource("data")
    public void canParseUtcTime(String message, Instant expected, String parseable) {
        Assertions.assertEquals(expected, Instant.from(DerValue.UTC_TIME_PATTERN.parse(parseable)), message);

    }

    @Test
    public void canUnmarshalDerUtcValue() {
        byte[] bytes = new byte[]{0x17, 0x0D, 0x31, 0x36, 0x30, 0x33, 0x31, 0x37, 0x31, 0x36, 0x34, 0x30, 0x34, 0x36, 0x5A};
        Instant got = DerCursor.flat(bytes)
                .next().utc(bytes);

        Assertions.assertEquals(
                Instant.parse("2016-03-17T16:40:46Z"),
                got,
                "the UTCTime value is decoded as an instant"
        );

    }
}
