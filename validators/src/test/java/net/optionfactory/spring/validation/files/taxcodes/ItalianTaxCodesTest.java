package net.optionfactory.spring.validation.files.taxcodes;

import java.util.stream.Stream;
import net.optionfactory.spring.validation.taxcodes.ItalianTaxCode;
import net.optionfactory.spring.validation.taxcodes.ItalianTaxCodes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ItalianTaxCodesTest {

    public static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of("LHBNVF51D21D265L", true),
                Arguments.of("HZCBVN68H66L384L", true),
                Arguments.of("CHVRNK30C68D561Y", true),
                Arguments.of("CHVRNK30C68D561", false),
                Arguments.of("CHVRNK30C68D561T", false)
        );
    }


    @ParameterizedTest
    @MethodSource("data")
    public void fiscalCode(String fiscalCode, boolean expectedValid) {
        Assertions.assertEquals(expectedValid, ItalianTaxCodes.isValid(fiscalCode, ItalianTaxCode.Type.CODICE_FISCALE));
    }
}
