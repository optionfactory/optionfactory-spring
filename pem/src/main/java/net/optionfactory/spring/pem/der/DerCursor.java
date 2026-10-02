package net.optionfactory.spring.pem.der;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// A forward-only reader of DER encoded ASN.1 values, yielding the tag and the content bounds of
/// each value without copying the source.
///
/// A cursor walks a region of the source in one of two [Navigation] modes: [Flat] yields the
/// values of the region as siblings, stepping over the content of the constructed ones, while
/// [Nested] steps into constructed values, yielding a container before its children in a
/// depth-first walk of the whole tree. Both views share the position of the cursor they come
/// from, so a walk can switch mode midway. A [DerValue] of a `SEQUENCE` or `SET` opens a new
/// cursor bounded to its content.
///
/// The decoding is strict DER: indefinite lengths, the long length form for lengths below 128 and
/// the high tag number form are rejected, as are lengths exceeding the region. Every failure is a
/// [DerException]. A cursor is not thread-safe.
///
/// ```java
/// final var cursor = DerCursor.nested(bytes);
/// cursor.next().ensure(Tag.SEQUENCE);
/// final BigInteger version = cursor.next().integer(bytes);
/// ```
public class DerCursor {

    private final byte[] source;
    private final int to;
    private int pos;

    /// @param source the DER encoded bytes
    /// @return a cursor over the whole source
    public static DerCursor of(byte[] source) {
        return new DerCursor(source);
    }

    /// @param source the DER encoded bytes
    /// @return a flat view of a cursor over the whole source
    public static DerCursor.Flat flat(byte[] source) {
        return new DerCursor(source).flat();
    }

    /// @param source the DER encoded bytes
    /// @return a nested view of a cursor over the whole source
    public static DerCursor.Nested nested(byte[] source) {
        return new DerCursor(source).nested();
    }

    /// @param source the DER encoded bytes, all of them to be read
    public DerCursor(byte[] source) {
        this.source = source;
        this.to = source.length;
        this.pos = 0;
    }

    /// Creates a cursor over a region of the source; the offsets of the values it yields are still
    /// relative to the whole source.
    ///
    /// @param source the DER encoded bytes
    /// @param from the offset of the first byte to read
    /// @param to the offset past the last byte to read
    public DerCursor(byte[] source, int from, int to) {
        this.source = source;
        this.to = to;
        this.pos = from;
    }

    /// How the cursor moves past a constructed value.
    public enum Navigation {
        /// Past the whole value, to its next sibling.
        FLAT,
        /// Into the content of the value, to its first child; primitive values are stepped over
        /// as in [#FLAT].
        NESTED;
    }

    /// A view of the cursor that moves in [Navigation#FLAT] mode.
    public class Flat {

        /// Fails unless the region is exhausted.
        ///
        /// @throws DerException when a value follows
        public void eof() {
            DerCursor.this.eof(Navigation.FLAT);
        }

        /// @return the next value
        /// @throws DerException when the region is exhausted or the value is malformed
        public DerValue next() {
            return DerCursor.this.next(Navigation.FLAT);
        }

        /// @return the next value, empty when the region is exhausted
        /// @throws DerException when the value is malformed
        public Optional<DerValue> mnext() {
            return DerCursor.this.mnext(Navigation.FLAT);
        }

        /// @return a nested view sharing the position of this one
        public Nested nested() {
            return new Nested();
        }

    }

    /// A view of the cursor that moves in [Navigation#NESTED] mode.
    public class Nested {

        /// Fails unless the region is exhausted.
        ///
        /// @throws DerException when a value follows
        public void eof() {
            DerCursor.this.eof(Navigation.NESTED);
        }

        /// @return the next value
        /// @throws DerException when the region is exhausted or the value is malformed
        public DerValue next() {
            return DerCursor.this.next(Navigation.NESTED);
        }

        /// @return the next value, empty when the region is exhausted
        /// @throws DerException when the value is malformed
        public Optional<DerValue> mnext() {
            return DerCursor.this.mnext(Navigation.NESTED);
        }

        /// @return a flat view sharing the position of this one
        public Flat flat() {
            return new Flat();
        }

    }

    /// Fails unless the region is exhausted. A value that follows is consumed before failing.
    ///
    /// @param n how to move past the value that follows, if any
    /// @throws DerException when a value follows
    public void eof(Navigation n) {
        DerException.ensure(mnext(n).isEmpty(), "expected EOF but source has more data");
    }

    /// @param n how to move past the yielded value
    /// @return the next value
    /// @throws DerException when the region is exhausted or the value is malformed
    public DerValue next(Navigation n) {
        return mnext(n).orElseThrow(() -> new DerException("EOF"));
    }

    /// @return a flat view sharing the position of this cursor
    public Flat flat() {
        return new Flat();
    }

    /// @return a nested view sharing the position of this cursor
    public Nested nested() {
        return new Nested();
    }

    /// Reads the tag and the length of the next value, then moves past it, or into it for a
    /// constructed value in [Navigation#NESTED] mode.
    ///
    /// @param n how to move past the yielded value
    /// @return the next value, empty when the region is exhausted
    /// @throws DerException when the value is malformed or exceeds the region
    public Optional<DerValue> mnext(Navigation n) {
        if (pos >= to) {
            return Optional.empty();
        }
        final var tag = tag();
        final int length = length();
        DerException.ensure(length <= to - pos, "declared length %d exceeds available bytes", length);
        final var value = new DerValue(tag, pos, pos + length);
        if (n == Navigation.FLAT || tag.isPrimitive()) {
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
        DerException.ensure(pos < to, "truncated length");
        int prefix = source[pos++];
        DerException.ensure((prefix & 0xff) != 0x80, "indeterminate lenght in DER encoding");
        if ((prefix & 0x080) == 0x00) {
            return prefix;
        }
        int lenInBytes = prefix & 0x07f;
        DerException.ensure(pos + lenInBytes <= to, "truncated length");
        int length = 0;
        for (int b = 0; b != lenInBytes; ++b) {
            length <<= 8;
            length += 0x0ff & source[pos++];
        }

        DerException.ensure(length >= 0, "negative length bytes");
        DerException.ensure(length > 127, "long form in use to encode a short form value");
        return length;
    }

    /// The identifier octet of a value: its class, its form and, in the low five bits, its tag
    /// number. The constants are the universal tag numbers, to compare with [#type()].
    ///
    /// @param data the identifier octet
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

        /// @return the tag number, without the class and form bits: `SEQUENCE` for `0x30`, but also
        /// `3` for the context specific `[3]`
        public byte type() {
            return (byte) (data & 0b00011111);
        }
        
        /// @return the name of the universal type the tag number denotes, `UNKNOWN(n)` for the
        /// numbers without a constant; the class is not considered
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
        
        /// @return the tag number, the same as [#type()], typically used to read the index of a
        /// context specific tag
        public long number() {
            return data & 0b00011111;
        }

        /// @return whether the class is universal
        public boolean isUniversal() {
            return (data & 0b11000000) == 0b00000000;
        }

        /// @return whether the class is application
        public boolean isApplication() {
            return (data & 0b11000000) == 0b01000000;
        }

        /// @return whether the class is context specific
        public boolean isContextSpecific() {
            return (data & 0b11000000) == 0b10000000;
        }

        /// @return whether the class is private
        public boolean isPrivate() {
            return (data & 0b11000000) == 0b11000000;
        }

        /// @return whether the value is constructed, its content being other values
        public boolean isStructured() {
            return (data & 0b00100000) == 0b00100000;
        }

        /// @return whether the value is primitive
        public boolean isPrimitive() {
            return (data & 0b00100000) == 0b00000000;
        }    
    }
    

    /// A value yielded by a cursor: its tag and the bounds of its content in the source.
    ///
    /// The accessors take the source the value was read from, and check that the tag is the
    /// universal tag of the type before decoding, failing with a [DerException] on a mismatch: an
    /// implicitly tagged value, such as a context specific `[2]`, is never read as the universal
    /// type sharing its tag number.
    ///
    /// @param tag the tag
    /// @param from the offset of the first content byte
    /// @param to the offset past the last content byte
    public record DerValue(Tag tag, int from, int to) {

        /// The `UTCTime` format: seconds are optional, and the time zone is `Z` or an offset such
        /// as `+0100`. The two-digit year is read as X.509 (RFC 5280) does: `50` to `99` as `19YY`,
        /// `00` to `49` as `20YY`.
        public static final DateTimeFormatter UTC_TIME_PATTERN = new DateTimeFormatterBuilder()
                .appendValueReduced(ChronoField.YEAR, 2, 2, 1950)
                .appendPattern("MMddHHmm[ss]XX")
                .toFormatter();

        /// @param tags the accepted universal tag numbers, without duplicates
        /// @return this value
        /// @throws DerException when the tag is not universal, or its number is none of `tags`
        public DerValue ensure(Byte... tags) {
            DerException.ensure(this.tag.isUniversal() && Set.of(tags).contains(this.tag.type()), "expected type to be one of %s but was: %s", List.of(tags), this.tag);
            return this;
        }
        /// @param index the expected tag number
        /// @return this value
        /// @throws DerException when the tag is not context specific, or its number is not `index`
        public DerValue ensureExplicitContextSpecific(long index) {
            DerException.ensure(this.tag.isContextSpecific(), "expected a context specific tag");
            DerException.ensure(this.tag.number() == index, "expected tag number %s got %s", index, this.tag.number());
            return this;
        }

        /// @param source the source the value was read from
        /// @return the two's complement `INTEGER`
        public BigInteger integer(byte[] source) {
            ensure(Tag.INTEGER);
            return new BigInteger(Arrays.copyOfRange(source, from, to));
        }

        /// @param source the source the value was read from
        /// @return the `ENUMERATED` value, truncated to its low 32 bits
        public int enumerated(byte[] source) {
            ensure(Tag.ENUMERATED);
            return new BigInteger(Arrays.copyOfRange(source, from, to)).intValue();
        }

        /// @param source the source the value was read from
        /// @return a copy of the `OCTET STRING` content
        public byte[] octets(byte[] source) {
            ensure(Tag.OCTETSTRING);
            return Arrays.copyOfRange(source, from, to);
        }

        /// @param source the source the value was read from
        /// @return a copy of the encoded `OBJECT IDENTIFIER` content, not decoded to its dotted form
        public byte[] oid(byte[] source) {
            ensure(Tag.OBJECTID);
            return Arrays.copyOfRange(source, from, to);
        }

        /// Reads the content of a `BIT STRING`, without its leading unused-bits octet; DER already
        /// zeroes the unused bits.
        ///
        /// @param source the source the value was read from
        /// @return a copy of the bits
        /// @throws DerException when the value is not a `BIT STRING`, or lacks the unused-bits octet
        public byte[] bits(byte[] source) {
            ensure(Tag.BITSTRING);
            DerException.ensure(to > from, "BIT STRING without the unused-bits octet");
            return Arrays.copyOfRange(source, from + 1, to);
        }

        /// @param source the source the value was read from
        /// @return the `UTCTime`, parsed with [#UTC_TIME_PATTERN]
        /// @throws java.time.format.DateTimeParseException when the content does not match the
        /// pattern
        public Instant utc(byte[] source) {
            ensure(Tag.UTCTIME);
            final var v = new String(source, from, to - from, StandardCharsets.UTF_8);
            return Instant.from(UTC_TIME_PATTERN.parse(v));
        }

        /// @param source the source the value was read from
        /// @return the `GeneralizedTime` as its raw text, not parsed
        public String time(byte[] source) {
            ensure(Tag.GENERALIZEDTIME);
            return new String(source, from, to - from, StandardCharsets.UTF_8);
        }

        /// @param source the source the value was read from
        /// @return a cursor bounded to the content of the `SET`
        public DerCursor set(byte[] source) {
            ensure(Tag.SET);
            return new DerCursor(source, from, to);
        }

        /// @param source the source the value was read from
        /// @return a cursor bounded to the content of the `SEQUENCE`
        public DerCursor sequence(byte[] source) {
            ensure(Tag.SEQUENCE);
            return new DerCursor(source, from, to);
        }

        /// The content of other constructed values, such as a context specific tag, is opened with
        /// [DerCursor#DerCursor(byte\[\], int, int)] on the value bounds.
        ///
        /// @param source the source the value was read from
        /// @return a cursor bounded to the content of the `SET` or `SEQUENCE`
        public DerCursor cursor(byte[] source) {
            ensure(Tag.SET, Tag.SEQUENCE);
            return new DerCursor(source, from, to);
        }

        /// @param source the source the value was read from
        /// @return the `BOOLEAN`: any non zero octet is true
        public boolean bool(byte[] source) {
            ensure(Tag.BOOLEAN);
            return source[from] != 0;
        }

        /// Decodes a character string with the charset of its type: US-ASCII for `PrintableString`,
        /// `IA5String` and `GeneralString`, ISO-8859-1 for `T61String`, UTF-16BE for `BMPString`,
        /// UTF-8 for `UTF8String` and UTF-32BE for `UniversalString`. The characters are not
        /// validated against the type.
        ///
        /// @param source the source the value was read from
        /// @return the decoded string
        /// @throws DerException when the value is not one of these universal string types
        public String string(byte[] source) {
            DerException.ensure(tag.isUniversal(), "expected type to be one of the string types but was: %s", tag);
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
