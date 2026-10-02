package net.optionfactory.spring.validation.taxcodes;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.spring.validation.taxcodes.ItalianTaxCodes.Type;

/// Requires a string to be an italian tax code: a *codice fiscale* (16 characters, the code of a
/// person), a *partita IVA* (11 digits, the VAT number of a business), or either, as chosen by
/// [#type()].
///
/// Only the check character is verified, as [ItalianTaxCodes#isValid(String, ItalianTaxCodes.Type)]
/// describes: a value with a correct check character is accepted even when, say, the birth date it
/// encodes does not exist. Codici fiscali with *omocodia* substitutions are accepted.
///
/// The value must be uppercase and contain no separators, unless [#lenient()] is set. The
/// constraint only checks: it does not change the value, so a lenient application should store
/// [ItalianTaxCodes#normalize(String)] of it.
///
/// The violation's message depends on the type, with
/// `{jakarta.validation.constraints.ItalianTaxCode.CODICE_FISCALE.message}`,
/// `{jakarta.validation.constraints.ItalianTaxCode.PARTITA_IVA.message}` and
/// `{jakarta.validation.constraints.ItalianTaxCode.ANY.message}`; [#message()] is never used.
///
/// `null` is valid, combine with `@NotNull` to require a value; an empty string is invalid.
///
/// ```java
/// public record Customer(@NotNull @ItalianTaxCode(type = Type.CODICE_FISCALE, lenient = true) String taxCode) {
/// }
/// ```
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ItalianTaxCodeValidator.class)
@Documented
public @interface ItalianTaxCode {

    /// @return which kind of tax code is accepted
    Type type() default Type.ANY;

    /// @return whether to [ItalianTaxCodes#normalize(String)] the value before validating it,
    ///         accepting lowercase letters, surrounding whitespace and separators
    boolean lenient() default false;

    /// Unused: the violation's message is chosen by [#type()].
    ///
    /// @return the message template
    String message() default "{jakarta.validation.constraints.ItalianTaxCode.message}";

    /// @return the validation groups the constraint belongs to
    Class<?>[] groups() default {};

    /// @return the payload attached to the constraint
    Class<? extends Payload>[] payload() default {};

}
