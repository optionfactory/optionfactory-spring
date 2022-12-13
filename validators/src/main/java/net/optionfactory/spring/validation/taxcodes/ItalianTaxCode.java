package net.optionfactory.spring.validation.taxcodes;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ItalianTaxCodeValidator.class)
@Documented
public @interface ItalianTaxCode {

    public enum Type {
        CODICE_FISCALE, PARTITA_IVA, ANY;
    }

    Type type() default Type.ANY;

    boolean lenient() default false;

    String message() default "{javax.validation.constraints.ItalianTaxCode.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
