package net.optionfactory.spring.validation.ibans;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IbansTest {

    @Test
    public void normalizationStripsSeparatorsAndUppercases() {
        Assertions.assertEquals("DE89370400440532013000", Ibans.normalize(" de89-3704 0044.0532 0130 00 "), "spaces, dashes and dots must be removed and letters uppercased");
    }

    @Test
    public void normalizationOfNullIsNull() {
        Assertions.assertNull(Ibans.normalize(null), "normalizing null must yield null");
    }

    @Test
    public void nullIsNotAValidIban() {
        Assertions.assertFalse(Ibans.isValid(null), "unlike the constraint, the utility must reject null");
    }

    @Test
    public void checkDigitsOutsideTheChecksumRangeAreRejected() {
        Assertions.assertFalse(Ibans.isValid("DE01370400440532013000"), "check digits 01 are never produced by the checksum and must be rejected");
        Assertions.assertFalse(Ibans.isValid("DE99370400440532013000"), "check digits 99 are never produced by the checksum and must be rejected");
    }

    @Test
    public void separatorsAreRejectedUnlessNormalizedFirst() {
        Assertions.assertFalse(Ibans.isValid("DE89 3704 0044 0532 0130 00"), "isValid must not strip inner spaces");
        Assertions.assertTrue(Ibans.isValid(Ibans.normalize("DE89 3704 0044 0532 0130 00")), "the normalized form of a valid IBAN must validate");
    }

    @Test
    public void lettersAreWorthTenToThirtyFiveInTheChecksum() {
        Assertions.assertEquals(7, Ibans.getNumericValue('7'), "a digit is worth itself");
        Assertions.assertEquals(10, Ibans.getNumericValue('A'), "A is worth 10");
        Assertions.assertEquals(35, Ibans.getNumericValue('Z'), "Z is worth 35");
    }
}
