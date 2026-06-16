package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;

public class Abbreviations {

    /**
     * Abbreviates a string to a strict maximum length by retaining its prefix
     * and suffix around an infix. Handles UTF-16 surrogate pairs to
     * prevent broken characters.
     *
     * @param source the original string to abbreviate.
     * @param infix the string to insert between the prefix and suffix
     * @param maxSize the maximum length of the final returned string.
     * @return the abbreviated string.
     */
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

    /**
     * Abbreviates a UTF-8 byte array to a maximum length by retaining its
     * prefix and suffix around an infix. Handles UTF-8 continuation bytes to
     * prevent malformed characters.
     *
     * @param utf8Bytes the original UTF-8 byte array to abbreviate.
     * @param infix the string to insert between the prefix and suffix
     * @param maxSize the maximum length of the final returned string.
     * @return the abbreviated string properly decoded in UTF-8.
     */
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
