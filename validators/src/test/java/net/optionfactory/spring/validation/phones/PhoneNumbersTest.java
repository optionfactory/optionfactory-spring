package net.optionfactory.spring.validation.phones;

import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import java.util.Optional;
import java.util.Set;
import net.optionfactory.spring.validation.phones.PhoneNumbers.PhoneValidationError;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PhoneNumbersTest {

    private static final Set<PhoneNumberType> MOBILES = Set.of(PhoneNumberType.MOBILE, PhoneNumberType.FIXED_LINE_OR_MOBILE);

    @Test
    public void aNationalNumberIsFormattedWithTheDefaultRegionPrefix() {
        Assertions.assertEquals("+393331234567", PhoneNumbers.e164Format("333 123 4567", "IT"), "a national number must be prefixed with the default region's country code");
    }

    @Test
    public void anInternationalNumberKeepsItsOwnPrefix() {
        Assertions.assertEquals("+12025550156", PhoneNumbers.e164Format("+1 (202) 555-0156", "IT"), "an explicit prefix must win over the default region");
    }

    @Test
    public void formattingAnEmptyNumberYieldsAnEmptyString() {
        Assertions.assertEquals("", PhoneNumbers.e164Format("", "IT"), "an empty number must format to an empty string");
        Assertions.assertEquals("", PhoneNumbers.e164Format(null, "IT"), "a null number must format to an empty string");
    }

    @Test
    public void formattingAnUnparseableNumberThrows() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> PhoneNumbers.e164Format("not-a-number", "IT"), "a value with no digits at all cannot be formatted");
    }

    @Test
    public void formattingCharactersAreToleratedByValidation() {
        Assertions.assertEquals(Optional.empty(), PhoneNumbers.validate("+39 (333) 123-4567", MOBILES, "IT"), "spaces, dashes and parentheses must not make a valid number invalid");
    }

    @Test
    public void aNullNumberIsAnInvalidNumber() {
        Assertions.assertEquals(Optional.of(PhoneValidationError.INVALID_NUMBER), PhoneNumbers.validate(null, MOBILES, "IT"), "unlike the constraint, the utility must reject null");
    }

    @Test
    public void theTypeMustBeExactlyOneOfTheAccepted() {
        Assertions.assertEquals(Optional.of(PhoneValidationError.INVALID_TYPE), PhoneNumbers.validate("3331234567", Set.of(PhoneNumberType.FIXED_LINE_OR_MOBILE), "IT"), "FIXED_LINE_OR_MOBILE must not admit a number classified as MOBILE");
    }
}
