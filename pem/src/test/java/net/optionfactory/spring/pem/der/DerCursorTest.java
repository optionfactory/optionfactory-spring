package net.optionfactory.spring.pem.der;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.pem.der.DerCursor.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DerCursorTest {

    /// An OCTET STRING (0x04) whose length field (0x82 0xFF 0xFF) claims 65535 bytes, while only one follows.
    @Test
    public void rejectsLengthExceedingAvailableBytes() {
        final byte[] malicious = new byte[]{0x04, (byte) 0x82, (byte) 0xFF, (byte) 0xFF, 0x41};
        Assertions.assertThrows(DerException.class, () -> DerCursor.of(malicious).flat().next(), "a declared length past the end of the source is rejected");
    }

    private static byte[] sample() throws IOException {
        final var seq = DerWriter.seq(DerWriter.integer(1), DerWriter.octetString(new byte[]{9, 9}));
        final var trailer = DerWriter.integer(2);
        final var out = new byte[seq.length + trailer.length];
        System.arraycopy(seq, 0, out, 0, seq.length);
        System.arraycopy(trailer, 0, out, seq.length, trailer.length);
        return out;
    }

    @Test
    public void flatNavigationSkipsTheContentOfConstructedValues() throws IOException {
        final var bytes = sample();
        final var cursor = DerCursor.flat(bytes);
        Assertions.assertEquals(Tag.SEQUENCE, cursor.next().tag().type(), "the first value is the sequence");
        Assertions.assertEquals(BigInteger.TWO, cursor.next().integer(bytes), "the sibling after the sequence comes next, its children skipped");
        Assertions.assertTrue(cursor.mnext().isEmpty(), "nothing follows the last sibling");
    }

    @Test
    public void nestedNavigationDescendsIntoConstructedValues() throws IOException {
        final var bytes = sample();
        final var cursor = DerCursor.nested(bytes);
        Assertions.assertEquals(Tag.SEQUENCE, cursor.next().tag().type(), "the container is yielded first");
        Assertions.assertEquals(BigInteger.ONE, cursor.next().integer(bytes), "then its first child");
        Assertions.assertArrayEquals(new byte[]{9, 9}, cursor.next().octets(bytes), "then its second child");
        Assertions.assertEquals(BigInteger.TWO, cursor.next().integer(bytes), "then the value following the container");
        cursor.eof();
    }

    @Test
    public void aContainerValueOpensACursorBoundedToItsContent() throws IOException {
        final var bytes = sample();
        final var inner = DerCursor.flat(bytes).next().sequence(bytes).flat();
        Assertions.assertEquals(BigInteger.ONE, inner.next().integer(bytes), "the sub-cursor starts at the first child");
        inner.next().ensure(Tag.OCTETSTRING);
        Assertions.assertTrue(inner.mnext().isEmpty(), "the sub-cursor ends with the container, before the trailing integer");
    }

    @Test
    public void nextPastTheEndFails() {
        final var bytes = new byte[]{Tag.NULL, 0};
        final var cursor = DerCursor.flat(bytes);
        cursor.next();
        Assertions.assertThrows(DerException.class, cursor::next, "next requires a value to be there");
    }

    @Test
    public void eofFailsWhenDataRemains() {
        final var bytes = new byte[]{Tag.NULL, 0};
        Assertions.assertThrows(DerException.class, () -> DerCursor.flat(bytes).eof(), "eof requires the source to be exhausted");
    }

    @Test
    public void rejectsIndefiniteLengths() {
        final var bytes = new byte[]{0x30, (byte) 0x80, 0x00, 0x00};
        Assertions.assertThrows(DerException.class, () -> DerCursor.flat(bytes).next(), "indefinite lengths are BER, not DER");
    }

    @Test
    public void rejectsLongFormForShortLengths() {
        final var bytes = new byte[]{Tag.OCTETSTRING, (byte) 0x81, 0x01, 0x41};
        Assertions.assertThrows(DerException.class, () -> DerCursor.flat(bytes).next(), "DER requires the short form for lengths up to 127");
    }

    @Test
    public void rejectsHighTagNumbers() {
        final var bytes = new byte[]{0x1F, 0x21, 0x00};
        Assertions.assertThrows(DerException.class, () -> DerCursor.flat(bytes).next(), "the high tag number form is not supported");
    }

    @Test
    public void typedAccessorsCheckTheTag() throws IOException {
        final var bytes = DerWriter.octetString(new byte[]{1});
        final var value = DerCursor.flat(bytes).next();
        Assertions.assertThrows(DerException.class, () -> value.integer(bytes), "an octet string is not read as an integer");
        Assertions.assertThrows(DerException.class, () -> value.ensure(Tag.INTEGER, Tag.BOOLEAN), "ensure fails when the type is none of the expected ones");
        Assertions.assertSame(value, value.ensure(Tag.INTEGER, Tag.OCTETSTRING), "ensure passes when the type is one of the expected ones");
    }

    @Test
    public void tagExposesClassAndForm() {
        final var contextSpecific = new Tag((byte) 0xA3);
        Assertions.assertTrue(contextSpecific.isContextSpecific(), "0xA3 is context specific");
        Assertions.assertTrue(contextSpecific.isStructured(), "0xA3 is constructed");
        Assertions.assertEquals(3, contextSpecific.number(), "0xA3 is tag number 3");
        final var sequence = new Tag((byte) 0x30);
        Assertions.assertTrue(sequence.isUniversal(), "0x30 is universal");
        Assertions.assertEquals("SEQUENCE", sequence.name(), "0x30 is a sequence");
        final var integer = new Tag(Tag.INTEGER);
        Assertions.assertTrue(integer.isPrimitive(), "an integer is primitive");
        Assertions.assertEquals("UNKNOWN(7)", new Tag((byte) 0x07).name(), "unnamed universal types report their number");
    }

    @Test
    public void ensureExplicitContextSpecificChecksClassAndNumber() throws IOException {
        final var bytes = DerWriter.explicit(2, DerWriter.integer(5));
        final var value = DerCursor.flat(bytes).next();
        Assertions.assertSame(value, value.ensureExplicitContextSpecific(2), "[2] matches index 2");
        Assertions.assertThrows(DerException.class, () -> value.ensureExplicitContextSpecific(1), "[2] does not match index 1");
        Assertions.assertEquals(BigInteger.valueOf(5), new DerCursor(bytes, value.from(), value.to()).flat().next().integer(bytes), "the explicitly tagged value is its content");
    }

    @Test
    public void stringsAreDecodedWithTheCharsetOfTheirType() {
        final var utf8 = "città".getBytes(StandardCharsets.UTF_8);
        final var utf8Der = new byte[2 + utf8.length];
        utf8Der[0] = Tag.UTF8STRING;
        utf8Der[1] = (byte) utf8.length;
        System.arraycopy(utf8, 0, utf8Der, 2, utf8.length);
        Assertions.assertEquals("città", DerCursor.flat(utf8Der).next().string(utf8Der), "UTF8String is decoded as UTF-8");

        final var bmp = new byte[]{Tag.BMPSTRING, 4, 0x00, 0x61, 0x20, (byte) 0xAC};
        Assertions.assertEquals("a€", DerCursor.flat(bmp).next().string(bmp), "BMPString is decoded as UTF-16BE");

        final var notAString = new byte[]{Tag.INTEGER, 1, 1};
        Assertions.assertThrows(DerException.class, () -> DerCursor.flat(notAString).next().string(notAString), "a non string type is not decoded as a string");
    }

    @Test
    public void booleansAndEnumeratedAreDecoded() {
        final var bytes = new byte[]{Tag.BOOLEAN, 1, (byte) 0xFF, Tag.BOOLEAN, 1, 0x00, Tag.ENUMERATED, 1, 0x03};
        final var cursor = DerCursor.flat(bytes);
        Assertions.assertTrue(cursor.next().bool(bytes), "0xFF is true");
        Assertions.assertFalse(cursor.next().bool(bytes), "0x00 is false");
        Assertions.assertEquals(3, cursor.next().enumerated(bytes), "the enumerated value is its integer");
    }
}
