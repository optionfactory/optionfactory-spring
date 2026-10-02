package net.optionfactory.spring.validation.ibans;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/// Validates a string against [Iban], delegating to [Ibans].
public class IbanValidator implements ConstraintValidator<Iban, String> {

    private boolean lenient;

    /// @param annotation the constraint
    @Override
    public void initialize(Iban annotation) {
        this.lenient = annotation.lenient();
    }

    /// @param value the IBAN to validate
    /// @param context the validation context
    /// @return true when the value is `null` or a valid IBAN, normalized first when the constraint is
    ///         lenient
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        final var normalized = lenient ? Ibans.normalize(value) : value;
        return Ibans.isValid(normalized);
    }

}
