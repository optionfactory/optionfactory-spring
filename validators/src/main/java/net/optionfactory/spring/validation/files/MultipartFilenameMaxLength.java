package net.optionfactory.spring.validation.files;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import org.springframework.web.multipart.MultipartFile;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import net.optionfactory.spring.validation.files.MultipartFilenameMaxLength.MultipartMaxFilenameLengthValidator;

/// Limits the length of an uploaded file's client-supplied original filename.
///
/// The limit is exclusive: a filename is valid only when it is strictly shorter than [#value()]
/// characters, so the default `256` admits filenames of up to 255 characters. The length is counted
/// in UTF-16 code units, as `String.length()` does.
///
/// A `null` file is valid, combine with `@NotNull` to require one. A file without an original
/// filename is invalid.
///
/// ```java
/// public record Upload(@NotNull @MultipartFilenameMaxLength(100) MultipartFile document) {
/// }
/// ```
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = MultipartMaxFilenameLengthValidator.class)
public @interface MultipartFilenameMaxLength {

    /// @return the length a filename must stay below
    long value() default 256;

    /// @return the message template
    String message() default "{jakarta.validation.constraints.MultipartFilenameMaxLength.message}";

    /// @return the validation groups the constraint belongs to
    Class<?>[] groups() default {};

    /// @return the payload attached to the constraint
    Class<? extends Payload>[] payload() default {};

    /// Validates a [MultipartFile] against [MultipartFilenameMaxLength].
    public static class MultipartMaxFilenameLengthValidator implements ConstraintValidator<MultipartFilenameMaxLength, MultipartFile> {

        private MultipartFilenameMaxLength annotation;

        /// @param constraintAnnotation the constraint
        @Override
        public void initialize(MultipartFilenameMaxLength constraintAnnotation) {
            this.annotation = constraintAnnotation;
        }

        /// @param value the file to validate
        /// @param context the validation context
        /// @return true when the file is `null` or its original filename is shorter than the limit,
        ///         false when it is not or the file has no original filename
        @Override
        public boolean isValid(MultipartFile value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            final var filename = value.getOriginalFilename();
            if (filename == null) {
                return false;
            }
            return !(filename.length() >= annotation.value());
        }
    }
}
