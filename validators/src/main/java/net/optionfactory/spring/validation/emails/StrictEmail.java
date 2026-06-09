package net.optionfactory.spring.validation.emails;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Email;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(StrictEmail.List.class)
@Constraint(validatedBy = {})
@Email(regexp = "^" + StrictEmail.LOCAL_PART + "@" + StrictEmail.DOMAIN_PART + "$", message = "{jakarta.validation.constraints.StrictEmail.message}")
public @interface StrictEmail {

    public static final String LOCAL_PART = "[a-zA-Z0-9_]+([+.-][a-zA-Z0-9_]*)*";
    public static final String DOMAIN_PART = "[a-zA-Z]?([a-zA-Z0-9-]+[.])+[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]";

    String message() default "{jakarta.validation.constraints.StrictEmail.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
    @Retention(RUNTIME)
    @Documented
    public @interface List {

        StrictEmail[] value();
    }

}
