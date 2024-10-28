package net.optionfactory.spring.pem.der;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
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

        public DerValue next() {
            return DerCursor.this.next(Navigation.NESTED);
        }

        public Optional<DerValue> mnext() {
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
        final var tag = tag();
        final int length = length();
        final var value = new DerValue(tag, pos, pos + length);
        if (n == Navigation.FLAT || tag.isPrimitive()) {
            //we don't advance the pos for containers so nested elements are yielded
            pos += length;
        }
        return Optional.of(value);
    }
    
    private Tag tag(){
        final byte value = source[pos++];
        DerException.ensure((value & 0b00011111) != 0b00011111, "high tag number form is not supported");
        return new Tag(value);
    }

    private int length() {
        int prefix = source[pos++];
        DerException.ensure((prefix & 0xff) != 0x80, "indeterminate lenght in DER encoding");
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

    public record Tag(byte data){
        
        public static final byte BOOLEAN = 0x01;
        public static final byte INTEGER = 0x02;
        public static final byte BITSTRING = 0x03;
        public static final byte OCTETSTRING = 0x04;
        public static final byte NULL = 0x05;
        public static final byte OBJECTID = 0x06;
        public static final byte ENUMERATED = 0x0A;
        public static final byte UTF8STRING = 0x0C;
        public static final byte PRINTABLESTRING = 0x13;
        public static final byte T61STRING = 0x14;
        public static final byte IA5STRING = 0x16;
        public static final byte UTCTIME = 0x17;
        public static final byte GENERALIZEDTIME = 0x18;
        public static final byte GENERALSTRING = 0x1B;
        public static final byte UNIVERSALSTRING = 0x1C;
        public static final byte BMPSTRING = 0x1E;
        public static final byte SEQUENCE = 0x10;
        public static final byte SET = 0x11;        

        public byte type() {
            return (byte) (data & 0b00011111);
        }
        
        public String name() {
            final var t = type();
            return switch(t){
                case Tag.BOOLEAN -> "BOOLEAN";
                case Tag.INTEGER -> "INTEGER";
                case Tag.BITSTRING -> "BITSTRING";
                case Tag.OCTETSTRING -> "OCTETSTRING";
                case Tag.NULL -> "NULL";
                case Tag.OBJECTID -> "OBJECTID";
                case Tag.ENUMERATED -> "ENUMERATED";
                case Tag.UTF8STRING -> "UTF8STRING";
                case Tag.PRINTABLESTRING -> "PRINTABLESTRING";
                case Tag.T61STRING -> "T61STRING";
                case Tag.IA5STRING -> "IA5STRING";
                case Tag.UTCTIME -> "UTCTIME";
                case Tag.GENERALIZEDTIME -> "GENERALIZEDTIME";
                case Tag.GENERALSTRING -> "GENERALSTRING";
                case Tag.UNIVERSALSTRING -> "UNIVERSALSTRING";
                case Tag.BMPSTRING -> "BMPSTRING";
                case Tag.SEQUENCE -> "SEQUENCE";
                case Tag.SET -> "SET";             
                default -> String.format("UNKNOWN(%s)", t);
            };
        }
        
        public long number() {
            return data & 0b00011111;
        }

        public boolean isUniversal() {
            return (data & 0b11000000) == 0b00000000;
        }

        public boolean isApplication() {
            return (data & 0b11000000) == 0b01000000;
        }

        public boolean isContextSpecific() {
            return (data & 0b11000000) == 0b10000000;
        }

        public boolean isPrivate() {
            return (data & 0b11000000) == 0b11000000;
        }

        public boolean isStructured() {
            return (data & 0b00100000) == 0b00100000;
        }

        public boolean isPrimitive() {
            return (data & 0b00100000) == 0b00000000;
        }    
    }
    

    public record DerValue(Tag tag, int from, int to) {


        public static final DateTimeFormatter UTC_TIME_PATTERN = DateTimeFormatter.ofPattern("yyMMddHHmm[ss]XX");

        public DerValue ensure(Byte... tags) {
            DerException.ensure(Set.of(tags).contains(this.tag.type()), "expected type to be one of %s but was: %s", List.of(tags), this.tag.type());
            return this;
        }
        public DerValue ensureExplicitContextSpecific(long index) {
            DerException.ensure(this.tag.isContextSpecific(), "expected a context specific tag");
            DerException.ensure(this.tag.number() == index, "expected tag number %s got %s", index, this.tag.number());
            return this;
        }

        public BigInteger integer(byte[] source) {
            ensure(Tag.INTEGER);
            return new BigInteger(Arrays.copyOfRange(source, from, to));
        }

        public int enumerated(byte[] source) {
            ensure(Tag.ENUMERATED);
            return new BigInteger(Arrays.copyOfRange(source, from, to)).intValue();
        }

        public byte[] octets(byte[] source) {
            ensure(Tag.OCTETSTRING);
            return Arrays.copyOfRange(source, from, to);
        }

        public byte[] oid(byte[] source) {
            ensure(Tag.OBJECTID);
            return Arrays.copyOfRange(source, from, to);
        }

        public byte[] bits(byte[] source) {
            ensure(Tag.OCTETSTRING);
            final var unusedBits = source[from];
            //unused bits are already zeroed in der.
            return Arrays.copyOfRange(source, from + 1, to);
        }

        public Instant utc(byte[] source) {
            ensure(Tag.UTCTIME);
            final var v = new String(source, from, to - from, StandardCharsets.UTF_8);
            return Instant.from(UTC_TIME_PATTERN.parse(v));
        }

        public String time(byte[] source) {
            ensure(Tag.GENERALIZEDTIME);
            return new String(source, from, to - from, StandardCharsets.UTF_8);
        }

        public DerCursor set(byte[] source) {
            ensure(Tag.SET);
            return new DerCursor(source, from, to);
        }

        public DerCursor sequence(byte[] source) {
            ensure(Tag.SEQUENCE);
            return new DerCursor(source, from, to);
        }

        public DerCursor cursor(byte[] source) {
            ensure(Tag.SET, Tag.SEQUENCE);
            return new DerCursor(source, from, to);
        }

        public boolean bool(byte[] source) {
            ensure(Tag.BOOLEAN);
            return source[from] != 0;
        }

        public String string(byte[] source) {
            return new String(source, from, to - from, switch (tag.type()) {
                case Tag.PRINTABLESTRING, Tag.IA5STRING, Tag.GENERALSTRING ->
                    StandardCharsets.US_ASCII;
                case Tag.T61STRING ->
                    StandardCharsets.ISO_8859_1;
                case Tag.BMPSTRING ->
                    StandardCharsets.UTF_16BE;
                case Tag.UTF8STRING ->
                    StandardCharsets.UTF_8;
                case Tag.UNIVERSALSTRING ->
                    Charset.forName("UTF-32BE");
                default ->
                    throw new DerException(String.format("expected type to be one of the string types but was: %s", tag));
            });
        }

    }

}
