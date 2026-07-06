package net.optionfactory.spring.validation.taxcodes;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ItalianTaxCodeValidator implements ConstraintValidator<ItalianTaxCode, String> {

    public static final int PARTITA_IVA_LENGTH = 11;
    public static final int CODICE_FISCALE_LENGTH = 16;
    private ItalianTaxCodes.Type type;
    private boolean lenient;

    @Override
    public void initialize(ItalianTaxCode annotation) {
        this.type = annotation.type();
        this.lenient = annotation.lenient();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        final String normalized = lenient ? ItalianTaxCodes.normalize(value) : value;
        if (ItalianTaxCodes.isValid(normalized, type)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        final var template = "{jakarta.validation.constraints.ItalianTaxCode.%s.message}".formatted(type);
        context.buildConstraintViolationWithTemplate(template).addConstraintViolation();
        return false;
    }

}
