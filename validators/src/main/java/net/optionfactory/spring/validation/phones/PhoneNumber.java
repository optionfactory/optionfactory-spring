package net.optionfactory.spring.validation.phones;

import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberValidator.class)
@Documented
public @interface PhoneNumber {

    
    PhoneNumberType[] types() default {
        PhoneNumberType.FIXED_LINE_OR_MOBILE, 
        PhoneNumberType.MOBILE
    };

    
    String defaultRegion() default "IT";

    String message() default "{jakarta.validation.constraints.PhoneNumber.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
