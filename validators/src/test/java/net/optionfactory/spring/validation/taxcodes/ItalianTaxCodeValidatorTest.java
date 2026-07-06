package net.optionfactory.spring.validation.taxcodes;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Optional;
import java.util.stream.Stream;
import net.optionfactory.spring.validation.taxcodes.ItalianTaxCodes.Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ItalianTaxCodeValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    public static Stream<Arguments> any() {
        return Stream.of(
                Arguments.of("LHBNVF51D21D265L", Optional.empty()),
                Arguments.of("HZCBVN68H66L384L", Optional.empty()),
                Arguments.of("CHVRNK30C68D561Y", Optional.empty()),
                Arguments.of("CHVRNK30C68D561", Optional.of("Invalid tax code or VAT number")),
                Arguments.of("CHVRNK30C68D561T", Optional.of("Invalid tax code or VAT number")),
                Arguments.of("ASD", Optional.of("Invalid tax code or VAT number")),
                Arguments.of("ZZNMPL36R44B898U", Optional.empty()),
                Arguments.of("ZZNMPL36R44B898T", Optional.of("Invalid tax code or VAT number")),
                Arguments.of("$ZNMPL36R44B898T", Optional.of("Invalid tax code or VAT number")),
                Arguments.of("ZZNMPL36R44B898*", Optional.of("Invalid tax code or VAT number")),
                Arguments.of("07643520567", Optional.empty()),
                Arguments.of("07643520567", Optional.empty()),
                Arguments.of("91044020310", Optional.empty()),
                Arguments.of("07643520563", Optional.of("Invalid tax code or VAT number")),
                Arguments.of("07643520563*", Optional.of("Invalid tax code or VAT number"))
        );
    }

    public record BeanWithTaxCode(@ItalianTaxCode(type = Type.ANY) String taxcode) {

    }

    @ParameterizedTest
    @MethodSource("any")
    public void any(String taxcode, Optional<String> expectedViolation) {
        final var bean = new BeanWithTaxCode(taxcode);
        final var violation = validator.validate(bean).stream().map(ConstraintViolation::getMessage).findFirst();
        Assertions.assertEquals(expectedViolation, violation, "%s is expected to be %svalid".formatted(taxcode, expectedViolation.isEmpty()? "": "in"));
    }

}
