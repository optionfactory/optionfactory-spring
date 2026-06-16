package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
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
        Assertions.assertEquals(expected, result);
    }
    
    
    
    @ParameterizedTest
    @MethodSource("dataForUtf16")
    public void canAbbreviateStringsWithFromString(String expected, String source, String infix, int maxSize) {
        final var result = Abbreviations.abbreviated(source, infix, maxSize);
        Assertions.assertEquals(expected, result);
    }

}
