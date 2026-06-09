package net.optionfactory.spring.validation.files.taxcodes;

import java.time.LocalDate;
import java.util.stream.Stream;
import net.optionfactory.spring.validation.taxcodes.ItalianTaxCodes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ItalianTaxCodeBirthDateTest {

    public static Stream<Arguments> birthDatesData() {
        return Stream.of(
                Arguments.of("FRRRRT79A10G920L", LocalDate.parse("1979-01-10")),
                Arguments.of("DMESPR80A01F205F", LocalDate.parse("1980-01-01")),
                Arguments.of("XUXSNY96P60Z210T", LocalDate.parse("1996-09-20")),
                Arguments.of("ABCDEF8LA01H501X", LocalDate.parse("1980-01-01")),
                Arguments.of("ABCDEF77BL5H501X", LocalDate.parse("1977-02-05")),
                Arguments.of("ABCDEFMNC20H501X", LocalDate.parse("2012-03-20")),
                Arguments.of("ABCDEF95ESQH501X", LocalDate.parse("1995-05-24")),
                Arguments.of("ABCDEFLLH15H501X", LocalDate.parse("2000-06-15")),
                Arguments.of("ABCDEFQQLMQH501X", LocalDate.parse("1944-07-14")),
                Arguments.of("ABCDEFV0RMNH501X", LocalDate.parse("1990-10-12")),
                Arguments.of("ABCDEF03P5LH501X", LocalDate.parse("2003-09-10")),
                Arguments.of("ABCDEF85TU1H501X", null),
                Arguments.of("ABCDEFUUMVVH501X", null)
        );
    }

    @ParameterizedTest
    @MethodSource("birthDatesData")
    public void canGuessBirthDate(String fiscalCode, LocalDate expected) {
        final var guessed = ItalianTaxCodes.guessBirthDate(fiscalCode, LocalDate.now());
        Assertions.assertEquals(expected, guessed, "%s was expected to yield %s".formatted(fiscalCode, expected));
    }

}
