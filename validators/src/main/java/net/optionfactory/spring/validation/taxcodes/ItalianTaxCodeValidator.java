package net.optionfactory.spring.validation.taxcodes;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class ItalianTaxCodeValidator implements ConstraintValidator<ItalianTaxCode, String> {

    public static final int PARTITA_IVA_LENGTH = 11;
    public static final int CODICE_FISCALE_LENGTH = 16;
    private ItalianTaxCode.Type type;
    private boolean lenient;

    @Override
    public void initialize(ItalianTaxCode annotation) {
        this.type = annotation.type();
        this.lenient = annotation.lenient();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintContext) {
        if (value == null) {
            return true;
        }
        final String normalized = lenient ? ItalianTaxCodes.normalize(value) : value;
        final int len = normalized.length();
        if (len == 0) {
            return true;
        }
        return ItalianTaxCodes.isValid(value, type);
    }


}
