package net.optionfactory.spring.validation.taxcodes;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

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

    String message() default "Codice fiscale/Partita IVA non validi";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
