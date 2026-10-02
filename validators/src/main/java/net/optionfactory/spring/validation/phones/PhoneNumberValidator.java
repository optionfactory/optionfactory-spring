package net.optionfactory.spring.validation.phones;

import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.EnumSet;
import java.util.List;

/// Validates a string against [PhoneNumber], delegating to
/// [PhoneNumbers#validate(String, java.util.Set, String)] and reporting the failure it returns with
/// that failure's own message template.
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    private EnumSet<PhoneNumberType> types;
    private String defaultRegion;

    /// @param annotation the constraint
    /// @throws IllegalArgumentException when the constraint accepts no type at all: no number could
    ///         ever be valid, so an empty [PhoneNumber#types()] is a configuration error
    @Override
    public void initialize(PhoneNumber annotation) {
        if (annotation.types().length == 0) {
            throw new IllegalArgumentException("@PhoneNumber must accept at least one type: types is empty");
        }
        this.types = EnumSet.copyOf(List.of(annotation.types()));
        this.defaultRegion = annotation.defaultRegion();
    }

    /// @param phoneNumber the number to validate
    /// @param context the validation context, whose default violation is replaced by one naming the
    ///        failure
    /// @return true when the number is `null` or a valid number of an accepted type
    @Override
    public boolean isValid(String phoneNumber, ConstraintValidatorContext context) {
        if (phoneNumber == null) {
            return true;
        }
        final var problems = PhoneNumbers.validate(phoneNumber, types, defaultRegion);
        if(problems.isEmpty()){
            return true;
        }
        context.disableDefaultConstraintViolation();
        final var template = "{jakarta.validation.constraints.PhoneNumber.%s.message}".formatted(problems.get());
        context.buildConstraintViolationWithTemplate(template).addConstraintViolation();
        return false;
    }

}
