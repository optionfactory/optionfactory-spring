package net.optionfactory.spring.validation.emails;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Email;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {})
@Email(regexp = "^" + StrictEmail.LOCAL_PART + "@" + StrictEmail.DOMAIN_PART + "$", message = "Specificare un indirizzo email valido")
public @interface StrictEmail {

    public static final String LOCAL_PART = "[a-zA-Z0-9_]+([+.-][a-zA-Z0-9_]+)*";
    public static final String DOMAIN_PART = "[a-zA-Z]?([a-zA-Z0-9-]+[.])+[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]";

    String message() default "{jakarta.validation.constraints.StrictEmail.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
