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
import net.optionfactory.spring.validation.files.MultipartFileMaxSize.MultipartFileSizeValidator;
import org.springframework.web.multipart.MultipartFile;

/// Limits the size of an uploaded file to [#value()] units of [#scale()], the limit included.
///
/// The size is the one `MultipartFile.getSize()` reports, so the check runs once the upload has
/// been received: it gives the client a field-level error, but does not protect the server from
/// large requests, which is the job of the multipart resolver's own limits.
///
/// A `null` file is valid, combine with `@NotNull` to require one.
///
/// The default message reads the limit as `{value}{scale}`, e.g. `1MiB`.
///
/// ```java
/// public record Upload(@NotNull @MultipartFileMaxSize(value = 500, scale = Scale.KB) MultipartFile avatar) {
/// }
/// ```
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultipartFileSizeValidator.class)
@Documented
public @interface MultipartFileMaxSize {

    /// @return the message template, interpolated with `value` and `scale`
    String message() default "{jakarta.validation.constraints.MultipartFileMaxSize.message}";

    /// @return the maximum size, in units of [#scale()]
    long value() default 1;

    /// @return the unit [#value()] is expressed in
    Scale scale() default Scale.MiB;

    /// The unit a size limit is expressed in, decimal (`KB`, `MB`) or binary (`KiB`, `MiB`).
    public enum Scale {
        /// Bytes.
        B(1),
        /// Kilobytes, 1000 bytes.
        KB(1000),
        /// Kibibytes, 1024 bytes.
        KiB(1024),
        /// Megabytes, 1000000 bytes.
        MB(1_000_000),
        /// Mebibytes, 1048576 bytes.
        MiB(1_048_576);
        /// The number of bytes in one unit.
        public final int bytes;

        Scale(int bytes) {
            this.bytes = bytes;
        }
    }

    /// @return the validation groups the constraint belongs to
    Class<?>[] groups() default {};

    /// @return the payload attached to the constraint
    Class<? extends Payload>[] payload() default {};

    /// Validates a [MultipartFile] against [MultipartFileMaxSize].
    public static class MultipartFileSizeValidator implements ConstraintValidator<MultipartFileMaxSize, MultipartFile> {

        private long maxSize;
        private Scale scale;

        /// @param annotation the constraint
        @Override
        public void initialize(MultipartFileMaxSize annotation) {
            this.maxSize = annotation.value();
            this.scale = annotation.scale();
        }

        /// @param value the file to validate
        /// @param context the validation context
        /// @return true when the file is `null` or no larger than the limit
        @Override
        public boolean isValid(MultipartFile value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.getSize() <= (maxSize * scale.bytes);
        }

    }
}
