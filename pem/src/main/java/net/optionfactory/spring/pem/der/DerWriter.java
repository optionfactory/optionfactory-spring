package net.optionfactory.spring.pem.der;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import net.optionfactory.spring.pem.der.DerCursor.Tag;

public class DerWriter {

    public static final int CONSTRUCTED = 0x20;
    public static final int CONTEXT_SPECIFIC = 0x80;

    private static final DateTimeFormatter UTC_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyMMddHHmmss'Z'")
            .withZone(ZoneId.of("UTC"));

    public static byte[] seq(byte[]... parts) throws IOException {
        return encodeTag(Tag.SEQUENCE | CONSTRUCTED, parts);
    }

    public static byte[] set(byte[]... parts) throws IOException {
        return encodeTag(Tag.SET | CONSTRUCTED, parts);
    }

    public static byte[] implicit(int tagNumber, byte[]... parts) throws IOException {
        // Tag: Context-Specific (0x80) | Constructed (0x20) | tagNumber
        // Example: [0] IMPLICIT -> 0xA0
        return encodeTag(CONTEXT_SPECIFIC | CONSTRUCTED | tagNumber, parts);
    }

    public static byte[] explicit(int tagNumber, byte[] data) throws IOException {
        // Tag: Context-Specific (0x80) | Constructed (0x20) | tagNumber
        // This is structurally same as implicit(), but semantically used to wrap existing DER data
        return encodeTag(CONTEXT_SPECIFIC | CONSTRUCTED |tagNumber, data);
    }

    public static byte[] integer(int value) throws IOException {
        return integer(BigInteger.valueOf(value));
    }

    public static byte[] integer(BigInteger value) throws IOException {
        return encodeTag(Tag.INTEGER, value.toByteArray());
    }

    public static byte[] octetString(byte[] data) throws IOException {
        return encodeTag(Tag.OCTETSTRING, data);
    }

    public static byte[] utcTime(Instant instant) throws IOException {
        String timeString = UTC_TIME_FORMATTER.format(instant);
        byte[] content = timeString.getBytes(StandardCharsets.US_ASCII);
        return encodeTag(Tag.UTCTIME, content);
    }

    public static byte[] nul() {
        return new byte[]{Tag.NULL, 0x00};
    }

    public static byte[] oid(String oid) throws IOException {
        String[] parts = oid.split("\\.");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        // First byte = 40 * first + second
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        buffer.write(first * 40 + second);
        for (int i = 2; i < parts.length; i++) {
            long val = Long.parseLong(parts[i]);
            writeOidComponent(buffer, val);
        }
        return encodeTag(Tag.OBJECTID, buffer.toByteArray());
    }

    private static byte[] encodeTag(int tag, byte[]... parts) throws IOException {
        int totalLength = 0;
        for (final var part : parts) {
            if (part != null) {
                totalLength += part.length;
            }
        }

        final var out = new ByteArrayOutputStream(totalLength + 5); // +5 for header estimate
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
        // Long form length
        int bytesNeeded = 0;
        int temp = length;
        while (temp > 0) {
            bytesNeeded++;
            temp >>= 8;
        }
        out.write(0x80 | bytesNeeded);
        for (int i = bytesNeeded - 1; i >= 0; i--) {
            out.write((length >> (i * 8)) & 0xFF);
        }
    }

    private static void writeOidComponent(ByteArrayOutputStream out, long val) {
        if (val < 128) {
            out.write((int) val);
            return;
        }
        // Write 7-bit chunks. Last chunk has high bit 0, others have high bit 1.
        // Stack storage to reverse order
        final var bytes = new ArrayList<Integer>();
        bytes.add((int) (val & 0x7F)); // Last byte (High bit 0)
        val >>= 7;
        while (val > 0) {
            bytes.add((int) (val & 0x7F) | 0x80); // Preceding bytes (High bit 1)
            val >>= 7;
        }
        // Big Endian
        for (int i = bytes.size() - 1; i >= 0; i--) {
            out.write(bytes.get(i));
        }
    }

}
