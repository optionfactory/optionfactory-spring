package net.optionfactory.spring.validation.files.taxcodes;

import java.time.LocalDate;
import java.util.stream.Stream;
import net.optionfactory.spring.validation.taxcodes.ItalianTaxCodes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ItalianTaxCodeBirthDateTest {

    public static Stream<Arguments> birthDatesData() {
        return Stream.of(
                Arguments.of("FRRRRT79A10G920L", LocalDate.parse("1979-01-10"), LocalDate.parse("2026-07-06"), 0),
                Arguments.of("DMESPR80A01F205F", LocalDate.parse("1980-01-01"), LocalDate.parse("2026-07-06"), 0),
                Arguments.of("XUXSNY96P60Z210T", LocalDate.parse("1996-09-20"), LocalDate.parse("2026-07-06"), 0),
                // homocody
                Arguments.of("ABCDEF8LA01H501X", LocalDate.parse("1980-01-01"), LocalDate.parse("2026-07-06"), 0),
                Arguments.of("ABCDEF77BL5H501X", LocalDate.parse("1977-02-05"), LocalDate.parse("2026-07-06"), 0),
                Arguments.of("ABCDEFMNC20H501X", LocalDate.parse("2012-03-20"), LocalDate.parse("2026-07-06"), 0),
                Arguments.of("ABCDEF95ESQH501X", LocalDate.parse("1995-05-24"), LocalDate.parse("2026-07-06"), 0),
                Arguments.of("ABCDEFLLH15H501X", LocalDate.parse("2000-06-15"), LocalDate.parse("2026-07-06"), 0),
                Arguments.of("ABCDEFQQLMQH501X", LocalDate.parse("1944-07-14"), LocalDate.parse("2026-07-06"), 0),
                Arguments.of("ABCDEFV0RMNH501X", LocalDate.parse("1990-10-12"), LocalDate.parse("2026-07-06"), 0),
                Arguments.of("ABCDEF03P5LH501X", LocalDate.parse("2003-09-10"), LocalDate.parse("2026-07-06"), 0),
                // invalid dates
                Arguments.of("ABCDEF85TU1H501X", null, LocalDate.parse("2026-07-06"), 0),
                Arguments.of("ABCDEFUUMVVH501X", null, LocalDate.parse("2026-07-06"), 0),
                Arguments.of("RSSMRA01B29H501X", null, LocalDate.parse("2026-07-06"), 0),
                // centenarians and infants
                Arguments.of("RSSMRA25A01H501X", LocalDate.parse("2025-01-01"), LocalDate.parse("2026-07-06"), 0),
                Arguments.of("RSSMRA25A01H501X", LocalDate.parse("1925-01-01"), LocalDate.parse("2026-07-06"), 18),
                // leap years
                Arguments.of("RSSMRA00B29H501X", LocalDate.parse("2000-02-29"), LocalDate.parse("2026-07-06"), 0),
                Arguments.of("RSSMRA00B69H501X", LocalDate.parse("2000-02-29"), LocalDate.parse("2026-07-06"), 0),
                Arguments.of("RSSMRALLB29H501X", LocalDate.parse("2000-02-29"), LocalDate.parse("2026-07-06"), 0),
                // Same Year, Later Month (Month/Day Cutoff Overlay)
                Arguments.of("RSSMRA26R01H501X", LocalDate.parse("1926-10-01"), LocalDate.parse("2026-07-06"), 0)
        );
    }

    @ParameterizedTest
    @MethodSource("birthDatesData")
    public void canGuessBirthDate(String fiscalCode, LocalDate expected, LocalDate referenceDate, int minAge) {
        final var guessed = ItalianTaxCodes.guessBirthDate(fiscalCode, referenceDate, minAge);
        Assertions.assertEquals(expected, guessed, "Code %s with referenceDate=%s and minAge=%s was expected to yield %s but got %s".formatted(fiscalCode, referenceDate, minAge, expected, guessed));
    }
}
