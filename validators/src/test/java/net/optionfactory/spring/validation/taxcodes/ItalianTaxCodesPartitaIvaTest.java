package net.optionfactory.spring.validation.taxcodes;

import net.optionfactory.spring.validation.taxcodes.ItalianTaxCodes.Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ItalianTaxCodesPartitaIvaTest {

    @Test
    public void nonDigitPartitaIvaIsRejected() {
        Assertions.assertFalse(ItalianTaxCodes.isValid("AAAAAAAAAA5", Type.PARTITA_IVA),
                "a partita IVA containing non-digit characters must not validate");
    }
}
