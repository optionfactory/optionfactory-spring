package net.optionfactory.spring.pem.der;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import net.optionfactory.spring.pem.der.DerCursor.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DerWriterTest {

    @Test
    public void oidWithLargeFirstSubidentifierIsBase128Encoded() throws IOException {
        final byte[] got = DerWriter.oid("2.100.3");
        Assertions.assertArrayEquals(new byte[]{0x06, 0x03, (byte) 0x81, 0x34, 0x03}, got, "2.100 is encoded as 180 in base 128 (0x81 0x34)");
    }

    @Test
    public void oidEncodesMultiByteArcs() throws IOException {
        final byte[] got = DerWriter.oid("1.2.840.113549.1.1.1");
        Assertions.assertArrayEquals(new byte[]{0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01}, got, "rsaEncryption has its well known encoding");
    }

    @Test
    public void oidWithTwoArcsOnly() throws IOException {
        Assertions.assertArrayEquals(new byte[]{0x06, 0x01, 0x2A}, DerWriter.oid("1.2"), "1.2 is the single byte 40 * 1 + 2");
    }

    @Test
    public void shortLengthsUseTheShortForm() throws IOException {
        final var got = DerWriter.octetString(new byte[127]);
        Assertions.assertArrayEquals(new byte[]{Tag.OCTETSTRING, 127}, Arrays.copyOf(got, 2), "127 is the longest length in short form");
    }

    @Test
    public void longLengthsUseTheMinimalLongForm() throws IOException {
        Assertions.assertArrayEquals(new byte[]{Tag.OCTETSTRING, (byte) 0x81, (byte) 200}, Arrays.copyOf(DerWriter.octetString(new byte[200]), 3), "a length under 256 takes one length byte");
        Assertions.assertArrayEquals(new byte[]{Tag.OCTETSTRING, (byte) 0x82, 0x01, 0x2C}, Arrays.copyOf(DerWriter.octetString(new byte[300]), 4), "a length under 65536 takes two length bytes");
    }

    @Test
    public void integersAreMinimalTwosComplement() throws IOException {
        Assertions.assertArrayEquals(new byte[]{Tag.INTEGER, 2, 0x00, (byte) 0x80}, DerWriter.integer(128), "a positive value with the high bit set gets a leading zero");
        Assertions.assertArrayEquals(new byte[]{Tag.INTEGER, 1, (byte) 0xFF}, DerWriter.integer(-1), "-1 is a single 0xFF");
        Assertions.assertArrayEquals(new byte[]{Tag.INTEGER, 1, 0x00}, DerWriter.integer(0), "zero takes one content byte");
    }

    @Test
    public void nullPartsAreSkipped() throws IOException {
        Assertions.assertArrayEquals(DerWriter.seq(DerWriter.oid("1.2")), DerWriter.seq(DerWriter.oid("1.2"), null), "a null part contributes nothing");
        Assertions.assertArrayEquals(new byte[]{0x30, 0x00}, DerWriter.seq((byte[]) null), "a sequence of a null part is empty");
    }

    @Test
    public void constructedTagsCarryClassAndFormBits() throws IOException {
        Assertions.assertEquals((byte) 0x30, DerWriter.seq(DerWriter.nul())[0], "a sequence is universal and constructed");
        Assertions.assertEquals((byte) 0x31, DerWriter.set(DerWriter.nul())[0], "a set is universal and constructed");
        Assertions.assertEquals((byte) 0xA1, DerWriter.implicit(1, DerWriter.nul(), DerWriter.nul())[0], "implicit [1] is context specific and constructed");
        Assertions.assertEquals((byte) 0xA0, DerWriter.explicit(0, DerWriter.nul())[0], "explicit [0] is context specific and constructed");
    }

    @Test
    public void utcTimeIsWrittenInUtcWithSeconds() throws IOException {
        final var got = DerWriter.utcTime(Instant.parse("2016-03-17T17:40:46+01:00"));
        Assertions.assertArrayEquals(new byte[]{0x17, 0x0D, 0x31, 0x36, 0x30, 0x33, 0x31, 0x37, 0x31, 0x36, 0x34, 0x30, 0x34, 0x36, 0x5A}, got, "the instant is written as YYMMDDhhmmssZ in UTC");
    }

    @Test
    public void writtenValuesAreReadBackByTheCursor() throws IOException {
        final var at = Instant.parse("2024-05-27T15:09:58Z");
        final var big = new BigInteger("123456789012345678901234567890");
        final var bytes = DerWriter.seq(
                DerWriter.integer(big),
                DerWriter.octetString(new byte[300]),
                DerWriter.utcTime(at),
                DerWriter.nul(),
                DerWriter.set(DerWriter.oid("1.2.840.113549.1.7.1"))
        );
        final var cursor = DerCursor.flat(bytes).next().sequence(bytes).flat();
        Assertions.assertEquals(big, cursor.next().integer(bytes), "the integer survives the round trip");
        Assertions.assertEquals(300, cursor.next().octets(bytes).length, "the long form length survives the round trip");
        Assertions.assertEquals(at, cursor.next().utc(bytes), "the time survives the round trip");
        cursor.next().ensure(Tag.NULL);
        final var set = cursor.next().set(bytes).flat();
        Assertions.assertArrayEquals(Arrays.copyOfRange(DerWriter.oid("1.2.840.113549.1.7.1"), 2, 11), set.next().oid(bytes), "the oid content survives the round trip");
        cursor.eof();
    }
}
