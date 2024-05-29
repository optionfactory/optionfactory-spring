package net.optionfactory.spring.pem.der;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public class DerCursor {

    private final byte[] source;
    private final int to;
    private int pos;

    public static DerCursor of(byte[] source) {
        return new DerCursor(source);
    }

    public static DerCursor.Flat flat(byte[] source) {
        return new DerCursor(source).flat();
    }

    public static DerCursor.Nested nested(byte[] source) {
        return new DerCursor(source).nested();
    }

    public DerCursor(byte[] source) {
        this.source = source;
        this.to = source.length;
        this.pos = 0;
    }

    public DerCursor(byte[] source, int from, int to) {
        this.source = source;
        this.to = to;
        this.pos = from;
    }

    public enum Navigation {
        FLAT, NESTED;
    }

    public class Flat {

        public void eof() {
            DerCursor.this.eof(Navigation.FLAT);
        }

        public DerValue next() {
            return DerCursor.this.next(Navigation.FLAT);
        }

        public Optional<DerValue> mnext() {
            return DerCursor.this.mnext(Navigation.FLAT);
        }

        public Nested nested() {
            return new Nested();
        }

    }

    public class Nested {

        public void eof() {
            DerCursor.this.eof(Navigation.NESTED);
        }

        public DerValue next(Navigation n) {
            return DerCursor.this.next(Navigation.NESTED);
        }

        public Optional<DerValue> mnext(Navigation n) {
            return DerCursor.this.mnext(Navigation.NESTED);
        }

        public Flat flat() {
            return new Flat();
        }

    }

    public void eof(Navigation n) {
        DerException.ensure(mnext(n).isEmpty(), "expected EOF but source has more data");
    }

    public DerValue next(Navigation n) {
        return mnext(n).orElseThrow(() -> new DerException("EOF"));
    }

    public Flat flat() {
        return new Flat();
    }

    public Nested nested() {
        return new Nested();
    }

    public Optional<DerValue> mnext(Navigation n) {
        if (pos >= to) {
            return Optional.empty();
        }
        final byte tag = source[pos++];
        final int length = length();
        final var value = new DerValue(tag, length, pos, pos + length);
        if (n == Navigation.FLAT && tag != DerValue.TAG_SEQUENCE && tag != DerValue.TAG_SET) {
            //we don't advance the pos for containers so nested elements are yielded
            pos += length;
        }
        return Optional.of(value);
    }

    private int length() {
        int prefix = source[pos++];
        if ((prefix & 0x080) == 0x00) {
            // short form: 1 byte
            return prefix;
        }
        // long form
        int lenInBytes = prefix & 0x07f;
        int length = 0;
        for (int b = 0; b != lenInBytes; ++b) {
            length <<= 8;
            length += 0x0ff & source[pos++];
        }

        DerException.ensure(length >= 0, "negative length bytes");
        DerException.ensure(length > 127, "long form in use to encode a short form value");
        return length;
    }

    public record DerValue(byte tag, long length, int from, int to) {

        public static final byte TAG_BOOLEAN = 0x01;
        public static final byte TAG_INTEGER = 0x02;
        public static final byte TAG_BITSTRING = 0x03;
        public static final byte TAG_OCTETSTRING = 0x04;
        public static final byte TAG_NULL = 0x05;
        public static final byte TAG_OBJECTID = 0x06;
        public static final byte TAG_ENUMERATED = 0x0A;
        public static final byte TAG_UTF8STRING = 0x0C;
        public static final byte TAG_PRINTABLESTRING = 0x13;
        public static final byte TAG_T61STRING = 0x14;
        public static final byte TAG_IA5STRING = 0x16;
        public static final byte TAG_UTCTIME = 0x17;
        public static final byte TAG_GENERALIZEDTIME = 0x18;
        public static final byte TAG_GENERALSTRING = 0x1B;
        public static final byte TAG_UNIVERSALSTRING = 0x1C;
        public static final byte TAG_BMPSTRING = 0x1E;
        public static final byte TAG_SEQUENCE = 0x30;
        public static final byte TAG_SET = 0x31;
        public static final DateTimeFormatter UTC_TIME_PATTERN = DateTimeFormatter.ofPattern("yyMMddHHmm[ss]XX");

        public DerValue ensure(Byte... tags) {
            DerException.ensure(Set.of(tags).contains(this.tag), "expected type to be one of %s but was: %s", tags, tag);
            return this;
        }

        public BigInteger integer(byte[] source) {
            ensure(DerValue.TAG_INTEGER);
            return new BigInteger(Arrays.copyOfRange(source, from, to));
        }

        public int enumerated(byte[] source) {
            ensure(DerValue.TAG_ENUMERATED);
            return new BigInteger(Arrays.copyOfRange(source, from, to)).intValue();
        }

        public byte[] octets(byte[] source) {
            ensure(DerValue.TAG_OCTETSTRING);
            return Arrays.copyOfRange(source, from, to);
        }

        public byte[] oid(byte[] source) {
            ensure(DerValue.TAG_OBJECTID);
            return Arrays.copyOfRange(source, from, to);
        }

        public byte[] bits(byte[] source) {
            ensure(DerValue.TAG_OCTETSTRING);
            final var unusedBits = source[from];
            //unused bits are already zeroed in der.
            return Arrays.copyOfRange(source, from + 1, to);
        }

        public Instant utc(byte[] source) {
            ensure(DerValue.TAG_UTCTIME);
            final var v = new String(source, from, to - from, StandardCharsets.UTF_8);
            return Instant.from(UTC_TIME_PATTERN.parse(v));
        }

        public String time(byte[] source) {
            ensure(DerValue.TAG_GENERALIZEDTIME);
            return new String(source, from, to - from, StandardCharsets.UTF_8);
        }

        public DerCursor set(byte[] source) {
            ensure(DerValue.TAG_SET);
            return new DerCursor(source, from, to);
        }

        public DerCursor sequence(byte[] source) {
            ensure(DerValue.TAG_SEQUENCE);
            return new DerCursor(source, from, to);
        }

        public DerCursor cursor(byte[] source) {
            ensure(DerValue.TAG_SET, DerValue.TAG_SEQUENCE);
            return new DerCursor(source, from, to);
        }

        public boolean bool(byte[] source) {
            ensure(DerValue.TAG_BOOLEAN);
            return source[from] != 0;
        }

        public String string(byte[] source) {
            return new String(source, from, to - from, switch (tag) {
                case TAG_PRINTABLESTRING, TAG_IA5STRING, TAG_GENERALSTRING ->
                    StandardCharsets.US_ASCII;
                case TAG_T61STRING ->
                    StandardCharsets.ISO_8859_1;
                case TAG_BMPSTRING ->
                    StandardCharsets.UTF_16BE;
                case TAG_UTF8STRING ->
                    StandardCharsets.UTF_8;
                case TAG_UNIVERSALSTRING ->
                    Charset.forName("UTF-32BE");
                default ->
                    throw new DerException(String.format("expected type to be one of the string types but was: %s", tag));
            });
        }

    }

}
