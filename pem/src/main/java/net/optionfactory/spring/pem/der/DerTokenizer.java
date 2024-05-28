package net.optionfactory.spring.pem.der;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;

public class DerTokenizer {

    private final byte[] source;
    private int pos;

    public DerTokenizer(byte[] source) {
        this.source = source;
        this.pos = 0;
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

        public BigInteger asBigInteger(byte[] source) {
            ensureTag(DerValue.TAG_INTEGER);
            return new BigInteger(Arrays.copyOfRange(source, from, to));
        }

        public DerValue ensureTag(int tag) {
            DerException.ensure(this.tag == tag, "expected type to be %s but was: %s", tag, this.tag);
            return this;
        }
    }

    public void ensureDone() {
        DerException.ensure(next().isEmpty(), "expected EOF but source has more data");
    }

    public DerValue ensureNext() {
        return next().orElseThrow(() -> new DerException("EOF"));
    }

    public Optional<DerValue> next() {
        if (pos >= source.length) {
            return Optional.empty();
        }
        final byte tag = source[pos++];
        final int length = length();
        final var value = new DerValue(tag, length, pos, pos + length);
        if (tag != DerValue.TAG_SEQUENCE && tag != DerValue.TAG_SET) {
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

}
