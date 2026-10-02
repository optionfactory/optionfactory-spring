package net.optionfactory.spring.validation.files;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import org.springframework.web.multipart.MultipartFile;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import net.optionfactory.spring.validation.files.MultipartFilenamePattern.MultipartFilenamePatternValidator;

/// Requires an uploaded file's client-supplied original filename to match a regular expression as
/// a whole.
///
/// The default pattern, `^[\w\-. ]+$`, admits ASCII letters, digits, `_`, `-`, `.` and spaces:
/// it keeps path separators, control characters and other characters that are awkward in a
/// filesystem or in a `Content-Disposition` header out of a name the application may reuse. Being
/// ASCII-only, it also rejects accented letters (`café.pdf`). The pattern is compiled once, without
/// flags.
///
/// A `null` file is valid, combine with `@NotNull` to require one. A file without an original
/// filename is invalid.
///
/// ```java
/// public record Upload(@NotNull @MultipartFilenamePattern("^[a-z0-9_-]+\\.pdf$") MultipartFile document) {
/// }
/// ```
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = MultipartFilenamePatternValidator.class)
public @interface MultipartFilenamePattern {

    /// @return the regular expression the whole original filename must match
    String value() default "^[\\w\\-. ]+$";

    /// @return the message template
    String message() default "{jakarta.validation.constraints.MultipartFilenamePattern.message}";

    /// @return the validation groups the constraint belongs to
    Class<?>[] groups() default {};

    /// @return the payload attached to the constraint
    Class<? extends Payload>[] payload() default {};

    /// Validates a [MultipartFile] against [MultipartFilenamePattern].
    public static class MultipartFilenamePatternValidator implements ConstraintValidator<MultipartFilenamePattern, MultipartFile> {

        private Pattern pattern;

        /// Compiles the pattern once.
        ///
        /// @param constraintAnnotation the constraint
        /// @throws java.util.regex.PatternSyntaxException when the pattern is not a valid regular
        ///         expression
        @Override
        public void initialize(MultipartFilenamePattern constraintAnnotation) {
            this.pattern = Pattern.compile(constraintAnnotation.value());
        }

        /// @param value the file to validate
        /// @param context the validation context
        /// @return true when the file is `null` or its whole original filename matches the
        ///         pattern, false when it does not or the file has no original filename
        @Override
        public boolean isValid(MultipartFile value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            final var filename = value.getOriginalFilename();
            if (filename == null) {
                return false;
            }
            return pattern.matcher(filename).matches();
        }
    }
}
