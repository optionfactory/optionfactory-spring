package net.optionfactory.spring.validation.ibans;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IbanValidator implements ConstraintValidator<Iban, String> {

    private boolean lenient;

    @Override
    public void initialize(Iban annotation) {
        this.lenient = annotation.lenient();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        final var normalized = lenient ? Ibans.normalize(value) : value;
        return Ibans.isValid(normalized);
    }

}
