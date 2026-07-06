package net.optionfactory.spring.validation.ibans;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IbanValidator.class)
@Documented
public @interface Iban {

    String message() default "{jakarta.validation.constraints.Iban.message}";

    boolean lenient() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
