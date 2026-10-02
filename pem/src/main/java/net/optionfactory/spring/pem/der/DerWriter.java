package net.optionfactory.spring.pem.der;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.optionfactory.spring.pem.der.DerCursor.Tag;

/// Builds DER encoded ASN.1 values bottom-up: each method returns the complete encoding of one
/// value, and constructed values take the encodings of their children.
///
/// Lengths are written in the minimal DER form. A `null` child is skipped, so an optional element
/// can be passed as it is. The values are not checked against ASN.1 rules: the caller is
/// responsible, for instance, for sorting the elements of a `SET OF`. The `IOException` the methods
/// declare comes from the in-memory stream they write to, and is not raised in practice.
///
/// ```java
/// final byte[] algorithm = DerWriter.seq(DerWriter.oid("1.2.840.113549.1.1.1"), DerWriter.nul());
/// ```
public class DerWriter {

    /// The form bit of a constructed value.
    public static final int CONSTRUCTED = 0x20;
    /// The class bits of a context specific tag.
    public static final int CONTEXT_SPECIFIC = 0x80;

    private static final DateTimeFormatter UTC_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyMMddHHmmss'Z'")
            .withZone(ZoneId.of("UTC"));

    private static final Instant UTC_TIME_MIN = Instant.parse("1950-01-01T00:00:00Z");
    private static final Instant UTC_TIME_MAX = Instant.parse("2050-01-01T00:00:00Z");

    /// @param part the encoded content, `null` for an empty sequence
    /// @return the `SEQUENCE`
    /// @throws IOException never in practice
    public static byte[] seq(byte[] part) throws IOException {
        return encodeTag(Tag.SEQUENCE | CONSTRUCTED, part);
    }

    /// @param parts the encoded elements, in order; `null` ones are skipped
    /// @return the `SEQUENCE`
    /// @throws IOException never in practice
    public static byte[] seq(byte[]... parts) throws IOException {
        return encodeTag(Tag.SEQUENCE | CONSTRUCTED, parts);
    }

    /// @param part the encoded content, `null` for an empty set
    /// @return the `SET`
    /// @throws IOException never in practice
    public static byte[] set(byte[] part) throws IOException {
        return encodeTag(Tag.SET | CONSTRUCTED, part);
    }

    /// @param parts the encoded elements, written in the given order (DER wants a `SET OF` sorted
    /// by encoding); `null` ones are skipped
    /// @return the `SET`
    /// @throws IOException never in practice
    public static byte[] set(byte[]... parts) throws IOException {
        return encodeTag(Tag.SET | CONSTRUCTED, parts);
    }

    /// Encodes a constructed `[tagNumber] IMPLICIT` value: the context specific tag replaces the
    /// tag of the value, so `part` is its content rather than a complete encoding. The constructed
    /// bit is set, as it must be for implicitly tagged constructed types such as `SEQUENCE`, `SET`
    /// and `SET OF`; a primitive type, such as an `OCTET STRING` or an `INTEGER`, is implicitly
    /// tagged with [#implicitPrimitive(int, byte\[\])].
    ///
    /// @param tagNumber the context specific tag number, below 31
    /// @param part the content
    /// @return the tagged value
    /// @throws IOException never in practice
    public static byte[] implicit(int tagNumber, byte[] part) throws IOException {
        return encodeTag(CONTEXT_SPECIFIC | CONSTRUCTED | tagNumber, part);
    }

    /// Encodes a constructed `[tagNumber] IMPLICIT` value whose content is the concatenation of
    /// `parts`, as for an implicitly tagged `SEQUENCE` or `SET OF`.
    ///
    /// @param tagNumber the context specific tag number, below 31
    /// @param parts the encoded elements; `null` ones are skipped
    /// @return the tagged value
    /// @throws IOException never in practice
    public static byte[] implicit(int tagNumber, byte[]... parts) throws IOException {
        return encodeTag(CONTEXT_SPECIFIC | CONSTRUCTED | tagNumber, parts);
    }

    /// Encodes a primitive `[tagNumber] IMPLICIT` value, as for an implicitly tagged
    /// `OCTET STRING` or `INTEGER`: the context specific tag replaces the tag of the value, so
    /// `content` is its content rather than a complete encoding, and the constructed bit is clear.
    ///
    /// @param tagNumber the context specific tag number, below 31
    /// @param content the content, `null` for an empty one
    /// @return the tagged value
    /// @throws IOException never in practice
    public static byte[] implicitPrimitive(int tagNumber, byte[] content) throws IOException {
        return encodeTag(CONTEXT_SPECIFIC | tagNumber, content);
    }

    /// Encodes a `[tagNumber] EXPLICIT` value: the context specific tag wraps the complete encoding
    /// of the value.
    ///
    /// @param tagNumber the context specific tag number, below 31
    /// @param data the encoded value
    /// @return the tagged value
    /// @throws IOException never in practice
    public static byte[] explicit(int tagNumber, byte[] data) throws IOException {
        return encodeTag(CONTEXT_SPECIFIC | CONSTRUCTED | tagNumber, data);
    }

    /// @param value the value
    /// @return the `INTEGER`, in minimal two's complement
    /// @throws IOException never in practice
    public static byte[] integer(int value) throws IOException {
        return integer(BigInteger.valueOf(value));
    }

    /// @param value the value
    /// @return the `INTEGER`, in minimal two's complement
    /// @throws IOException never in practice
    public static byte[] integer(BigInteger value) throws IOException {
        return encodeTag(Tag.INTEGER, value.toByteArray());
    }

    /// @param data the octets
    /// @return the `OCTET STRING`
    /// @throws IOException never in practice
    public static byte[] octetString(byte[] data) throws IOException {
        return encodeTag(Tag.OCTETSTRING, data);
    }

    /// Encodes `instant` as `YYMMDDhhmmssZ` in UTC, truncated to the second. The two-digit year,
    /// read as X.509 (RFC 5280) does, covers 1950 to 2049 only: instants outside that range, for
    /// which X.509 asks for a `GeneralizedTime`, are rejected.
    ///
    /// @param instant the instant
    /// @return the `UTCTime`
    /// @throws IOException never in practice
    /// @throws IllegalArgumentException when `instant` is before 1950 or after 2049
    public static byte[] utcTime(Instant instant) throws IOException {
        if (instant.isBefore(UTC_TIME_MIN) || !instant.isBefore(UTC_TIME_MAX)) {
            throw new IllegalArgumentException(String.format("UTCTime cannot represent %s: only 1950 to 2049 are", instant));
        }
        String timeString = UTC_TIME_FORMATTER.format(instant);
        byte[] content = timeString.getBytes(StandardCharsets.US_ASCII);
        return encodeTag(Tag.UTCTIME, content);
    }

    /// @return the `NULL`, a new array on each call
    public static byte[] nul() {
        return new byte[]{Tag.NULL, 0x00};
    }

    /// @param oid the dotted form, with at least two arcs, such as `1.2.840.113549.1.1.1`
    /// @return the `OBJECT IDENTIFIER`
    /// @throws IOException never in practice
    /// @throws IllegalArgumentException when `oid` has a single arc
    /// @throws NumberFormatException when an arc is not a number
    public static byte[] oid(String oid) throws IOException {
        final var buffer = new ByteArrayOutputStream();
        
        int firstDot = oid.indexOf('.');
        if (firstDot == -1) {
            throw new IllegalArgumentException(String.format("an OBJECT IDENTIFIER has at least two arcs: %s", oid));
        }
        int secondDot = oid.indexOf('.', firstDot + 1);
        
        int first = Integer.parseInt(oid, 0, firstDot, 10);
        int second;
        if (secondDot == -1) {
            second = Integer.parseInt(oid, firstDot + 1, oid.length(), 10);
        } else {
            second = Integer.parseInt(oid, firstDot + 1, secondDot, 10);
        }
        writeOidComponent(buffer, first * 40L + second);
        
        int start = secondDot + 1;
        while (secondDot != -1) {
            int nextDot = oid.indexOf('.', start);
            long val;
            if (nextDot == -1) {
                val = Long.parseLong(oid, start, oid.length(), 10);
                secondDot = -1;
            } else {
                val = Long.parseLong(oid, start, nextDot, 10);
                start = nextDot + 1;
                secondDot = nextDot;
            }
            writeOidComponent(buffer, val);
        }
        return encodeTag(Tag.OBJECTID, buffer.toByteArray());
    }

    private static byte[] encodeTag(int tag, byte[] part) throws IOException {
        int totalLength = part != null ? part.length : 0;
        final var out = new ByteArrayOutputStream(totalLength + 5); 
        out.write(tag);
        writeLength(out, totalLength);
        if (part != null) {
            out.write(part);
        }
        return out.toByteArray();
    }

    private static byte[] encodeTag(int tag, byte[]... parts) throws IOException {
        int totalLength = 0;
        for (final var part : parts) {
            if (part != null) {
                totalLength += part.length;
            }
        }

        final var out = new ByteArrayOutputStream(totalLength + 5);
        out.write(tag);
        writeLength(out, totalLength);

        for (final var part : parts) {
            if (part != null) {
                out.write(part);
            }
        }
        return out.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream out, int length) {
        if (length < 128) {
            out.write(length);
            return;
        }
        if (length <= 0xFF) {
            out.write(0x81);
            out.write(length);
        } else if (length <= 0xFFFF) {
            out.write(0x82);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        } else if (length <= 0xFFFFFF) {
            out.write(0x83);
            out.write((length >> 16) & 0xFF);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        } else {
            out.write(0x84);
            out.write((length >> 24) & 0xFF);
            out.write((length >> 16) & 0xFF);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        }
    }

    private static void writeOidComponent(ByteArrayOutputStream out, long val) {
        if (val < 128) {
            out.write((int) val);
            return;
        }
        final byte[] buf = new byte[10];
        int idx = buf.length;
        buf[--idx] = (byte) (val & 0x7F);
        val >>= 7;
        while (val > 0) {
            buf[--idx] = (byte) ((val & 0x7F) | 0x80);
            val >>= 7;
        }
        out.write(buf, idx, buf.length - idx);
    }
}