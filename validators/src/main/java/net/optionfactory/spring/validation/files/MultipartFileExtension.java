package net.optionfactory.spring.validation.files;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.stream.Stream;
import net.optionfactory.spring.validation.files.MultipartFileExtension.MultipartFileExtensionValidator;
import org.springframework.web.multipart.MultipartFile;

/// Restricts an uploaded file to the extensions of its client-supplied original filename.
///
/// The extension is whatever follows the last `.` of `MultipartFile.getOriginalFilename()`, so
/// `archive.tar.gz` has the extension `gz`, and it is compared case-insensitively with each of the
/// [#types()], which are given without the leading dot. Like the filename itself, the extension is
/// what the client claims: this is a usability check, not a security one.
///
/// A `null` file is valid, combine with `@NotNull` to require one. A file without an original
/// filename, without a `.` in it, or ending with a `.` is invalid.
///
/// The default message lists the accepted [#types()].
///
/// ```java
/// public record Upload(@NotNull @MultipartFileExtension(types = {"pdf", "png"}) MultipartFile document) {
/// }
/// ```
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultipartFileExtensionValidator.class)
@Documented
public @interface MultipartFileExtension {

    /// @return the message template, interpolated with the accepted `types`
    String message() default "{jakarta.validation.constraints.MultipartFileExtension.message}";

    /// @return the accepted extensions, without the leading dot, compared ignoring case
    String[] types();

    /// @return the validation groups the constraint belongs to
    Class<?>[] groups() default {};

    /// @return the payload attached to the constraint
    Class<? extends Payload>[] payload() default {};

    /// Validates a [MultipartFile] against [MultipartFileExtension].
    public static class MultipartFileExtensionValidator implements ConstraintValidator<MultipartFileExtension, MultipartFile> {

        private String[] types;

        /// @param annotation the constraint
        @Override
        public void initialize(MultipartFileExtension annotation) {
            this.types = annotation.types();
        }

        /// @param value the file to validate
        /// @param context the validation context
        /// @return true when the file is `null` or the extension of its original filename is one of
        ///         the accepted ones, false otherwise
        @Override
        public boolean isValid(MultipartFile value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            final var originalFilename = value.getOriginalFilename();
            if (originalFilename == null) {
                return false;
            }
            final var lastIndexOfDot = originalFilename.lastIndexOf('.');
            if (lastIndexOfDot == -1) {
                return false;
            }
            final var extension = originalFilename.substring(lastIndexOfDot + 1);
            return Stream.of(types).anyMatch(t -> extension.equalsIgnoreCase(t));
        }

    }
}
