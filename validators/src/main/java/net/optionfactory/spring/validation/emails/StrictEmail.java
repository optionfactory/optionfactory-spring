package net.optionfactory.spring.validation.emails;

import jakarta.validation.Constraint;
import jakarta.validation.OverridesAttribute;
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

/// An email address constraint stricter than jakarta's `@Email`: it accepts the plain
/// `local@domain.tld` addresses people actually type, and rejects the exotic forms RFC 5322 allows
/// but that a signup or contact form has no use for.
///
/// The constraint is composed with `@Email`, so the bean validation provider's own email checks
/// (hibernate validator's, for instance, limits the local part to 64 characters) run as well, and the value must also match `^` [#LOCAL_PART] `@` [#DOMAIN_PART] `$` as a whole. In
/// practice this rejects:
///
/// - quoted local parts, and any character in the local part other than ASCII letters, digits and
///   `_`, `+`, `.`, `-`; a local part starting with a separator;
/// - ip literals (`user@[127.0.0.1]`), domains without a dot (`admin@mailserver`) and any
///   character in the domain other than ASCII letters, digits, `-` and `.`;
/// - a top level domain of a single character;
/// - leading or trailing whitespace: the value is not trimmed.
///
/// `null` is valid, as for every constraint: combine with `@NotNull` to require a value. An empty
/// string is invalid.
///
/// The violation is the one reported by the composing `@Email`, whose message is overridden by
/// [#message()]: by default `{jakarta.validation.constraints.StrictEmail.message}`, localized in
/// english, italian, french and spanish by the `ContributorValidationMessages` bundle this module
/// ships.
///
/// ```java
/// public record Signup(@NotNull @StrictEmail String email) {
/// }
/// ```
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(StrictEmail.List.class)
@Constraint(validatedBy = {})
@Email(regexp = "^" + StrictEmail.LOCAL_PART + "@" + StrictEmail.DOMAIN_PART + "$", message = "{jakarta.validation.constraints.StrictEmail.message}")
public @interface StrictEmail {

    /// The regular expression for the part before the `@`: a run of ASCII letters, digits and `_`,
    /// followed by any number of `+`, `.` or `-` separators each followed by a possibly empty run.
    /// The expression alone admits `a..b` and `a.`: such local parts are rejected by `@Email`'s own
    /// checks, while `someone.-85` passes both.
    public static final String LOCAL_PART = "[a-zA-Z0-9_]+([+.-][a-zA-Z0-9_]*)*";

    /// The regular expression for the part after the `@`: one or more dot-terminated labels of ASCII
    /// letters, digits and `-`, then a top level label of at least two characters that neither starts
    /// nor ends with `-`.
    public static final String DOMAIN_PART = "[a-zA-Z]?([a-zA-Z0-9-]+[.])+[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]";

    /// @return the message template, propagated to the composing `@Email` that reports the violation
    @OverridesAttribute(constraint = Email.class, name = "message")
    String message() default "{jakarta.validation.constraints.StrictEmail.message}";

    /// @return the validation groups the constraint belongs to, propagated to the composing `@Email`
    Class<?>[] groups() default {};

    /// @return the payload attached to the constraint
    Class<? extends Payload>[] payload() default {};

    /// Holds several [StrictEmail] constraints on the same element, typically declared for
    /// different groups.
    @Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
    @Retention(RUNTIME)
    @Documented
    public @interface List {

        /// @return the repeated constraints
        StrictEmail[] value();
    }

}
