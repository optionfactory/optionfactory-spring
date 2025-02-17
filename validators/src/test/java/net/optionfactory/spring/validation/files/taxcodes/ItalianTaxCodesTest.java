package net.optionfactory.spring.validation.files.taxcodes;

import java.util.List;
import net.optionfactory.spring.validation.taxcodes.ItalianTaxCode;
import net.optionfactory.spring.validation.taxcodes.ItalianTaxCodes;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ItalianTaxCodesTest {

    private static Object[] expect(String taxcode, boolean expected) {
        return new Object[]{
            taxcode, expected, expected ? "valid" : "invalid"
        };
    }

    @Parameters(name = "{0} expected to be {2}")
    public static List<Object[]> data() {
        return List.of(
                expect("LHBNVF51D21D265L", true),
                expect("HZCBVN68H66L384L", true),
                expect("CHVRNK30C68D561Y", true),
                expect("CHVRNK30C68D561", false),
                expect("CHVRNK30C68D561T", false)
        );
    }

    private final String fiscalCode;
    private final boolean expected;

    public ItalianTaxCodesTest(String fiscalCode, boolean expected, String e) {
        this.fiscalCode = fiscalCode;
        this.expected = expected;
    }

    @Test
    public void fiscalCode() {
        Assert.assertEquals(expected, ItalianTaxCodes.isValid(fiscalCode, ItalianTaxCode.Type.CODICE_FISCALE));
    }
}
