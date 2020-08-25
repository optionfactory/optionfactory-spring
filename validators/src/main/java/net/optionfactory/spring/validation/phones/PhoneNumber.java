package net.optionfactory.spring.validation.phones;

import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

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

    String message() default "Numero di telefono non valido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
