package net.optionfactory.spring.pem.der;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.optionfactory.spring.pem.der.DerCursor.Tag;

public class DerWriter {

    public static final int CONSTRUCTED = 0x20;
    public static final int CONTEXT_SPECIFIC = 0x80;

    private static final DateTimeFormatter UTC_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyMMddHHmmss'Z'")
            .withZone(ZoneId.of("UTC"));

    private static final byte[] NULL_BYTES = new byte[]{Tag.NULL, 0x00};

    public static byte[] seq(byte[] part) throws IOException {
        return encodeTag(Tag.SEQUENCE | CONSTRUCTED, part);
    }

    public static byte[] seq(byte[]... parts) throws IOException {
        return encodeTag(Tag.SEQUENCE | CONSTRUCTED, parts);
    }

    public static byte[] set(byte[] part) throws IOException {
        return encodeTag(Tag.SET | CONSTRUCTED, part);
    }

    public static byte[] set(byte[]... parts) throws IOException {
        return encodeTag(Tag.SET | CONSTRUCTED, parts);
    }

    public static byte[] implicit(int tagNumber, byte[] part) throws IOException {
        return encodeTag(CONTEXT_SPECIFIC | CONSTRUCTED | tagNumber, part);
    }

    public static byte[] implicit(int tagNumber, byte[]... parts) throws IOException {
        return encodeTag(CONTEXT_SPECIFIC | CONSTRUCTED | tagNumber, parts);
    }

    public static byte[] explicit(int tagNumber, byte[] data) throws IOException {
        return encodeTag(CONTEXT_SPECIFIC | CONSTRUCTED | tagNumber, data);
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
        return NULL_BYTES;
    }

    public static byte[] oid(String oid) throws IOException {
        final var buffer = new ByteArrayOutputStream();
        
        int firstDot = oid.indexOf('.');
        int secondDot = oid.indexOf('.', firstDot + 1);
        
        int first = Integer.parseInt(oid, 0, firstDot, 10);
        int second;
        if (secondDot == -1) {
            second = Integer.parseInt(oid, firstDot + 1, oid.length(), 10);
        } else {
            second = Integer.parseInt(oid, firstDot + 1, secondDot, 10);
        }
        buffer.write(first * 40 + second);
        
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
        // A 64-bit long fits inside 10 base-128 bytes max        
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