package net.optionfactory.spring.validation.ibans;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class IbanValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private record StrictBean(@Iban(lenient = false) String iban) {

    }

    private record LenientBean(@Iban(lenient = true) String iban) {

    }

    public static Stream<Arguments> ibanData() {
        return Stream.of(
                // { IBAN, lenient mode, expected valid, reason }

                // 1. Jakarta Validator Basics
                Arguments.of(null, false, true, "Null is handled by @NotNull, not constraint validators"),
                Arguments.of(null, true, true, "Null is valid even in lenient mode"),
                Arguments.of("", false, false, "Empty string fails minimum length check"),
                Arguments.of("IT", false, false, "Too short to be an IBAN"),
                // 2. Mathematically Valid IBANs (Real, proven IBANs)
                Arguments.of("DE89370400440532013000", false, true, "Valid German IBAN"),
                Arguments.of("NL91ABNA0417164300", false, true, "Valid Dutch IBAN"),
                // 3. Lowercase and Whitespace Padding 
                Arguments.of(" de89370400440532013000 ", false, true, "Valid IBAN padded with whitespace and lowercase"),
                Arguments.of("nl91abna0417164300", false, true, "Valid IBAN entirely in lowercase"),
                // 4. Lenient Formatting (Spaces and dashes)
                Arguments.of("DE89 3704 0044 0532 0130 00", false, false, "Strict mode rejects spaces"),
                Arguments.of("DE89 3704 0044 0532 0130 00", true, true, "Lenient mode accepts and strips spaces"),
                Arguments.of("NL91-ABNA-0417-1643-00", false, false, "Strict mode rejects dashes"),
                Arguments.of("NL91-ABNA-0417-1643-00", true, true, "Lenient mode accepts and strips dashes"),
                // 5. Invalid Country Codes and Lengths
                Arguments.of("XX89370400440532013000", false, false, "Unrecognized country code 'XX'"),
                Arguments.of("DE8937040044053201300", false, false, "German IBAN is too short (19 chars)"),
                Arguments.of("DE893704004405320130001", false, false, "German IBAN is too long (21 chars)"),
                // 6. Invalid Formats (Regex Failures)
                Arguments.of("NL9112340417164300", false, false, "Dutch IBAN requires 4 letters, numbers provided"),
                // 7. Invalid Checksums
                Arguments.of("DE12370400440532013000", false, false, "Valid German IBAN but with bad check digit '12'"),
                Arguments.of("NL15ABNA0417164300", false, false, "Valid Dutch IBAN but with bad check digit '15'"),
                Arguments.of("NL00ABNA0417164300", false, false, "'00' check digit is explicitly rejected by standard")
        );
    }

    @ParameterizedTest(name = "[{index}] {3}: ''{0}'' (lenient={1}) -> {2}")
    @MethodSource("ibanData")
    public void canValidateIban(String iban, boolean lenient, boolean expectedValid, String reason) {
        final var targetBean = lenient ? new LenientBean(iban) : new StrictBean(iban);
        final var violations = validator.validate(targetBean);

        Assertions.assertEquals(expectedValid, violations.isEmpty(),
                String.format("Test Failed: %s. Expected valid=%s for IBAN '%s' (lenient=%s)",
                        reason, expectedValid, iban, lenient));
    }
}
