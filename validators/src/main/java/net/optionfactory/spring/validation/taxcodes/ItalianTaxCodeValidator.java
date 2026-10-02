package net.optionfactory.spring.validation.taxcodes;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/// Validates a string against [ItalianTaxCode], delegating to [ItalianTaxCodes] and reporting a
/// failure with the message template of the constraint's type.
public class ItalianTaxCodeValidator implements ConstraintValidator<ItalianTaxCode, String> {

    /// The length of a partita IVA.
    public static final int PARTITA_IVA_LENGTH = 11;
    /// The length of a codice fiscale.
    public static final int CODICE_FISCALE_LENGTH = 16;
    private ItalianTaxCodes.Type type;
    private boolean lenient;

    /// @param annotation the constraint
    @Override
    public void initialize(ItalianTaxCode annotation) {
        this.type = annotation.type();
        this.lenient = annotation.lenient();
    }

    /// @param value the tax code to validate
    /// @param context the validation context, whose default violation is replaced by the one of the
    ///        constraint's type
    /// @return true when the value is `null` or a valid tax code of the accepted type, normalized
    ///         first when the constraint is lenient
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
