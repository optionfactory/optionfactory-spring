package net.optionfactory.spring.marshaling.jackson.quirks;

import java.util.stream.Stream;
import net.optionfactory.spring.marshaling.jackson.quirks.text.ScreamQuirkHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CamelCaseToSnakeCaseTest {

    private final ScreamQuirkHandler.CamelCaseToSnakeCase transformer = new ScreamQuirkHandler.CamelCaseToSnakeCase();

    public static Stream<Arguments> camelToScreamData() {
        return Stream.of(
                Arguments.of("myVariable", "MY_VARIABLE"),
                Arguments.of("mySuperVariable", "MY_SUPER_VARIABLE"),
                Arguments.of("value", "VALUE"),
                Arguments.of("a", "A"),
                Arguments.of("A", "A"),
                Arguments.of("", "")
        );
    }

    @ParameterizedTest(name = "transform({0}) should yield {1}")
    @MethodSource("camelToScreamData")
    public void canTransformCamelToScreamSnake(String camel, String expectedScreamSnake) {
        Assertions.assertEquals(expectedScreamSnake, transformer.transform(camel));
    }

    public static Stream<Arguments> screamToCamelData() {
        return Stream.of(
                Arguments.of("MY_VARIABLE", "myVariable"),
                Arguments.of("MY_SUPER_VARIABLE", "mySuperVariable"),
                Arguments.of("MY__VARIABLE", "myVariable"),
                Arguments.of("MY___VARIABLE", "myVariable"),
                Arguments.of("SUPER__LONG___NAME", "superLongName"),
                Arguments.of("_MY_VARIABLE", "MyVariable"),
                Arguments.of("MY_VARIABLE_", "myVariable"),
                Arguments.of("A", "a"),
                Arguments.of("a", "a"),
                Arguments.of("", "")
        );
    }

    @ParameterizedTest(name = "reverse({0}) should yield {1}")
    @MethodSource("screamToCamelData")
    public void canReverseScreamSnakeToCamel(String screamSnake, String expectedCamel) {
        Assertions.assertEquals(expectedCamel, transformer.reverse(screamSnake));
    }
}
