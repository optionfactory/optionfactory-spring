package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;

/// Shortens the payloads rendered in logs and alerts, keeping their beginning and their end around
/// an infix (the `✂️` of `@Upstream.Logging` by default).
public class Abbreviations {

    /// Abbreviates a string to at most `maxSize` chars, keeping a prefix and a suffix of the source
    /// around the infix; the prefix gets the extra char when the room left is odd.
    ///
    /// A surrogate pair is never split: the prefix and the suffix shrink by one char instead, so
    /// the result can be one or two chars shorter than `maxSize`. An infix longer than `maxSize`
    /// is returned whole, and the result is then longer than `maxSize`.
    ///
    /// @param source the string to abbreviate
    /// @param infix the string replacing the middle of the source
    /// @param maxSize the maximum length, in chars, of the result
    /// @return the source itself when it fits, the abbreviated string otherwise
    public static String abbreviated(String source, String infix, int maxSize) {
        if (source.length() <= maxSize) {
            return source;
        }

        final int availableSize = Math.max(0, maxSize - infix.length());

        final int initialPrefixLength = (availableSize + 1) / 2;
        int prefixEnd = initialPrefixLength;

        if (prefixEnd > 0 && prefixEnd < source.length()
                && Character.isHighSurrogate(source.charAt(prefixEnd - 1))
                && Character.isLowSurrogate(source.charAt(prefixEnd))) {
            prefixEnd--;
        }

        final int initialSuffixLength = availableSize - initialPrefixLength;
        int suffixStart = source.length() - initialSuffixLength;

        if (suffixStart > 0 && suffixStart < source.length()
                && Character.isHighSurrogate(source.charAt(suffixStart - 1))
                && Character.isLowSurrogate(source.charAt(suffixStart))) {
            suffixStart++;
        }

        final var prefix = source.substring(0, prefixEnd);
        final var suffix = source.substring(suffixStart);

        return prefix + infix + suffix;
    }

    /// Abbreviates UTF-8 bytes to at most `maxSize` bytes, keeping a prefix and a suffix of the
    /// source around the infix, and decodes the result.
    ///
    /// Sizes are counted in UTF-8 bytes, the infix's included. A multi-byte character is never
    /// split: the prefix and the suffix shrink to the closest character boundary instead. An infix
    /// longer than `maxSize` is returned whole, and the result is then longer than `maxSize`.
    ///
    /// @param utf8Bytes the UTF-8 encoded text to abbreviate
    /// @param infix the string replacing the middle of the source
    /// @param maxSize the maximum length, in UTF-8 bytes, of the result
    /// @return the decoded source when it fits, the decoded abbreviation otherwise
    public static String abbreviated(byte[] utf8Bytes, String infix, int maxSize) {
        if (utf8Bytes.length <= maxSize) {
            return new String(utf8Bytes, StandardCharsets.UTF_8);
        }
        final var infixBytes = infix.getBytes(StandardCharsets.UTF_8);
        final int availableSize = Math.max(0, maxSize - infixBytes.length);

        final int initialPrefixLength = (availableSize + 1) / 2;
        int prefixEnd = initialPrefixLength;
        while (prefixEnd > 0 && (utf8Bytes[prefixEnd] & 0xC0) == 0x80) {
            prefixEnd--;
        }

        final int initialSuffixLength = availableSize - initialPrefixLength;
        int suffixStart = utf8Bytes.length - initialSuffixLength;
        while (suffixStart < utf8Bytes.length && (utf8Bytes[suffixStart] & 0xC0) == 0x80) {
            suffixStart++;
        }

        final var prefix = new String(utf8Bytes, 0, prefixEnd, StandardCharsets.UTF_8);
        final var suffix = new String(utf8Bytes, suffixStart, utf8Bytes.length - suffixStart, StandardCharsets.UTF_8);

        return prefix + infix + suffix;
    }
}
