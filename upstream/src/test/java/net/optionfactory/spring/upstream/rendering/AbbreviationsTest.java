package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class AbbreviationsTest {

    public static Stream<Arguments> dataForUtf8() {
        return Stream.of(
                Arguments.of(".", "ab", ".", 1),
                Arguments.of("ab", "ab", ".", 2),
                Arguments.of(".", "abc", ".", 1),
                Arguments.of("a.", "abc", ".", 2),
                Arguments.of("abc", "abc", ".", 3),
                Arguments.of("…", "ab", "…", 1),
                Arguments.of("…", "abc", "…", 1),
                Arguments.of("…", "abc", "…", 2)
        );
    }

    public static Stream<Arguments> dataForUtf16() {
        return Stream.of(
                Arguments.of(".", "ab", ".", 1),
                Arguments.of("ab", "ab", ".", 2),
                Arguments.of(".", "abc", ".", 1),
                Arguments.of("a.", "abc", ".", 2),
                Arguments.of("abc", "abc", ".", 3),
                Arguments.of("…", "ab", "…", 1),
                Arguments.of("…", "abc", "…", 1),
                Arguments.of("a…", "abc", "…", 2)
        );
    }
    
    @ParameterizedTest
    @MethodSource("dataForUtf8")
    public void canAbbreviateStringsWithFromUtf8Bytes(String expected, String source, String infix, int maxSize) {
        final var bytes = source.getBytes(StandardCharsets.UTF_8);
        final var result = Abbreviations.abbreviated(bytes, infix, maxSize);
        Assertions.assertEquals(expected, result, "the abbreviation must fit maxSize UTF-8 bytes, infix included");
    }

    @ParameterizedTest
    @MethodSource("dataForUtf16")
    public void canAbbreviateStringsWithFromString(String expected, String source, String infix, int maxSize) {
        final var result = Abbreviations.abbreviated(source, infix, maxSize);
        Assertions.assertEquals(expected, result, "the abbreviation must fit maxSize chars, infix included");
    }

    @Test
    public void aSourceThatFitsIsReturnedAsItIs() {
        final var source = "abcd";
        Assertions.assertSame(source, Abbreviations.abbreviated(source, ".", 4), "a string within maxSize must be returned unchanged");
        Assertions.assertEquals("àbcd", Abbreviations.abbreviated("àbcd".getBytes(StandardCharsets.UTF_8), ".", 5), "bytes within maxSize must be decoded unchanged");
    }

    @Test
    public void theRoomIsSplitBetweenPrefixAndSuffixFavouringThePrefix() {
        Assertions.assertEquals("abc.ij", Abbreviations.abbreviated("abcdefghij", ".", 6), "the prefix must get the extra char when the room left is odd");
        Assertions.assertEquals("abc.ij", Abbreviations.abbreviated("abcdefghij".getBytes(StandardCharsets.UTF_8), ".", 6), "the prefix must get the extra byte when the room left is odd");
    }

    @Test
    public void surrogatePairsAreNeverSplit() {
        final var source = "a\uD83D\uDE00b\uD83D\uDE00";
        final var got = Abbreviations.abbreviated(source, ".", 4);
        Assertions.assertEquals("a.", got, "the prefix and the suffix must shrink rather than split a surrogate pair");
    }

    @Test
    public void multiByteCharactersAreNeverSplit() {
        final var source = "aàbcdèf".getBytes(StandardCharsets.UTF_8);
        final var got = Abbreviations.abbreviated(source, ".", 6);
        Assertions.assertEquals("aà.f", got, "the prefix and the suffix must shrink to a character boundary rather than split a character");
    }

}
