package net.optionfactory.spring.validation.ibans;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Requires a string to be a valid IBAN, as checked by [Ibans#isValid(String)]: a known country,
/// the national length and format, and the mod 97 checksum.
///
/// Surrounding whitespace and lowercase letters are always tolerated. Spaces, dashes and other
/// separators within the IBAN, as people often type it, are rejected unless [#lenient()] is set.
/// The constraint only checks: it does not change the value, so a lenient application should
/// store [Ibans#normalize(String)] of it.
///
/// `null` is valid, combine with `@NotNull` to require a value; an empty string is invalid.
///
/// ```java
/// public record Payee(@NotNull @Iban(lenient = true) String iban) {
/// }
/// ```
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IbanValidator.class)
@Documented
public @interface Iban {

    /// @return the message template
    String message() default "{jakarta.validation.constraints.Iban.message}";

    /// @return whether to [Ibans#normalize(String)] the value before validating it, accepting
    ///         separators and any other character that is not an ASCII letter or digit
    boolean lenient() default false;

    /// @return the validation groups the constraint belongs to
    Class<?>[] groups() default {};

    /// @return the payload attached to the constraint
    Class<? extends Payload>[] payload() default {};

}
