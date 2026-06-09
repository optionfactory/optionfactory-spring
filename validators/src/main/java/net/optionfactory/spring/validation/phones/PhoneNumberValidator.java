package net.optionfactory.spring.validation.phones;

import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.EnumSet;
import java.util.List;

public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    private EnumSet<PhoneNumberType> types;
    private String defaultRegion;

    @Override
    public void initialize(PhoneNumber annotation) {
        this.types = EnumSet.copyOf(List.of(annotation.types()));
        this.defaultRegion = annotation.defaultRegion();
    }

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
