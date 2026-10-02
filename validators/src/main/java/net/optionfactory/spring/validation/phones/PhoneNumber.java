package net.optionfactory.spring.validation.phones;

import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Requires a string to be a valid phone number of one of the accepted [#types()], as judged by
/// google's libphonenumber.
///
/// A number written without an international prefix is read as a number of [#defaultRegion()]; one
/// with a `+` prefix is read as written, whatever the default region. Formatting characters such as
/// spaces, dashes and parentheses are tolerated by the parser. The constraint only checks: it does
/// not change the value, so an application should store [PhoneNumbers#e164Format(String, String)]
/// of it.
///
/// The violation tells the two failures apart, with
/// `{jakarta.validation.constraints.PhoneNumber.INVALID_NUMBER.message}` for a value that is not a
/// valid number and `{jakarta.validation.constraints.PhoneNumber.INVALID_TYPE.message}` for a valid
/// number of a type not accepted; [#message()] is never used.
///
/// `null` is valid, combine with `@NotNull` to require a value; an empty or blank string is an
/// invalid number.
///
/// ```java
/// public record Contact(@NotNull @PhoneNumber(types = PhoneNumberType.MOBILE) String mobile) {
/// }
/// ```
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberValidator.class)
@Documented
public @interface PhoneNumber {

    /// The default accepts mobile numbers, including those of regions where mobile and fixed line
    /// numbers cannot be told apart (`FIXED_LINE_OR_MOBILE`), but not numbers libphonenumber knows
    /// to be fixed lines, toll free or premium rate: an Italian landline is rejected.
    ///
    /// An empty array fails the validator's initialization.
    ///
    /// @return the accepted number types
    PhoneNumberType[] types() default {
        PhoneNumberType.FIXED_LINE_OR_MOBILE, 
        PhoneNumberType.MOBILE
    };

    /// @return the two-letter region code, e.g. `IT` or `US`, numbers without an international prefix are
    ///         read in
    String defaultRegion() default "IT";

    /// Unused: the violation's message is chosen by the kind of failure.
    ///
    /// @return the message template
    String message() default "{jakarta.validation.constraints.PhoneNumber.message}";

    /// @return the validation groups the constraint belongs to
    Class<?>[] groups() default {};

    /// @return the payload attached to the constraint
    Class<? extends Payload>[] payload() default {};

}
