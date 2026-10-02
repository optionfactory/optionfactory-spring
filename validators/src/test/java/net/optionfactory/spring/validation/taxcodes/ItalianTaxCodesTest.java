package net.optionfactory.spring.validation.taxcodes;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.optionfactory.spring.validation.taxcodes.ItalianTaxCodes.Type;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ItalianTaxCodesTest {

    private static final String CODICE_FISCALE = "LHBNVF51D21D265L";
    private static final String PARTITA_IVA = "07643520567";

    private static final Validator VALIDATOR = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.ENGLISH, false))
            .buildValidatorFactory()
            .getValidator();

    public record CodiceFiscale(@ItalianTaxCode(type = Type.CODICE_FISCALE) String taxcode) {

    }

    public record PartitaIva(@ItalianTaxCode(type = Type.PARTITA_IVA) String taxcode) {

    }

    public record Lenient(@ItalianTaxCode(lenient = true) String taxcode) {

    }

    public record Strict(@ItalianTaxCode String taxcode) {

    }

    private static List<String> messages(Object bean) {
        return VALIDATOR.validate(bean).stream().map(cv -> cv.getMessage()).toList();
    }

    @Test
    public void normalizationStripsSeparatorsAndUppercases() {
        Assertions.assertEquals(CODICE_FISCALE, ItalianTaxCodes.normalize(" lhb-nvf 51d21.d265l "), "whitespace and separators must be removed and letters uppercased");
        Assertions.assertNull(ItalianTaxCodes.normalize(null), "normalizing null must yield null");
    }

    @Test
    public void theRequestedTypeRestrictsTheAcceptedLength() {
        Assertions.assertTrue(ItalianTaxCodes.isValid(CODICE_FISCALE, Type.CODICE_FISCALE), "a valid codice fiscale must be accepted as such");
        Assertions.assertFalse(ItalianTaxCodes.isValid(CODICE_FISCALE, Type.PARTITA_IVA), "a valid codice fiscale must be rejected when only a partita IVA is accepted");
        Assertions.assertTrue(ItalianTaxCodes.isValid(PARTITA_IVA, Type.PARTITA_IVA), "a valid partita IVA must be accepted as such");
        Assertions.assertFalse(ItalianTaxCodes.isValid(PARTITA_IVA, Type.CODICE_FISCALE), "a valid partita IVA must be rejected when only a codice fiscale is accepted");
    }

    @Test
    public void nullAndEmptyAreNotValidTaxCodes() {
        Assertions.assertFalse(ItalianTaxCodes.isValid(null, Type.ANY), "unlike the constraint, the utility must reject null");
        Assertions.assertFalse(ItalianTaxCodes.isValid("", Type.ANY), "an empty string must be rejected");
    }

    @Test
    public void lowercaseIsRejectedUnlessLenient() {
        final var lowercase = CODICE_FISCALE.toLowerCase(Locale.ROOT);
        Assertions.assertEquals(1, messages(new Strict(lowercase)).size(), "a lowercase codice fiscale must be rejected by default");
        Assertions.assertEquals(List.of(), messages(new Lenient(lowercase)), "a lenient constraint must normalize a lowercase codice fiscale first");
    }

    @Test
    public void separatorsAreAcceptedWhenLenient() {
        Assertions.assertEquals(List.of(), messages(new Lenient("076 435 205 67")), "a lenient constraint must strip the spaces grouping a partita IVA");
    }

    @Test
    public void theViolationMessageDependsOnTheType() {
        Assertions.assertEquals(List.of("Invalid tax code"), messages(new CodiceFiscale("ASD")), "a CODICE_FISCALE constraint must report an invalid tax code");
        Assertions.assertEquals(List.of("Invalid VAT number"), messages(new PartitaIva("ASD")), "a PARTITA_IVA constraint must report an invalid VAT number");
    }

    @Test
    public void theCheckDigitOfAPartitaIvaIsComputedFromItsFirstTenDigits() {
        Assertions.assertEquals(Optional.of('7'), ItalianTaxCodes.controlCodePartitaIva("0764352056"), "the check digit of 0764352056 is 7");
        Assertions.assertEquals(Optional.empty(), ItalianTaxCodes.controlCodePartitaIva(null), "no check digit can be computed for null");
    }

    @Test
    public void theCheckCharacterOfACodiceFiscaleIsComputedFromItsFirstFifteenCharacters() {
        Assertions.assertEquals(Optional.of('L'), ItalianTaxCodes.controlCodeCodiceFiscale("LHBNVF51D21D265"), "the check character of LHBNVF51D21D265 is L");
        Assertions.assertEquals(Optional.empty(), ItalianTaxCodes.controlCodeCodiceFiscale("lhbnvf51d21d265"), "lowercase characters have no value in the check table");
    }

    @Test
    public void noBirthDateIsGuessedFromAValueOfTheWrongLength() {
        Assertions.assertNull(ItalianTaxCodes.guessBirthDate(PARTITA_IVA, LocalDate.parse("2026-07-06"), 0), "a partita IVA encodes no birth date");
        Assertions.assertNull(ItalianTaxCodes.guessBirthDate(null, LocalDate.parse("2026-07-06"), 0), "null encodes no birth date");
    }

    @Test
    public void theBirthDateIsGuessedFromANonNormalizedValue() {
        Assertions.assertEquals(LocalDate.parse("1951-04-21"), ItalianTaxCodes.guessBirthDate(" lhbnvf51d21d265l ", LocalDate.parse("2026-07-06"), 0), "the value must be normalized before decoding");
    }
}
