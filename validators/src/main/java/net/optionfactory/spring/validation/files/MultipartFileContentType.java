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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.validation.files.MultipartFileContentType.MultipartFileContentTypeValidator;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

/// Restricts an uploaded file to the media types its client declared, as reported by
/// `MultipartFile.getContentType()`.
///
/// The declared type is matched with `MediaType.includes`, so a wildcard such as `image/*` admits
/// every image subtype, and parameters are ignored: `text/plain` admits
/// `text/plain;charset=UTF-8`. The content type is what the client claims, not what the bytes
/// are: this is a usability check, not a security one.
///
/// A `null` file is valid, combine with `@NotNull` to require one. A file without a content type is
/// invalid. A content type spring cannot parse makes the validator throw, which the bean validation
/// provider reports as a `ValidationException` rather than as a violation.
///
/// The default message lists the accepted [#types()].
///
/// ```java
/// public record Upload(@NotNull @MultipartFileContentType(types = {"image/*", "application/pdf"}) MultipartFile document) {
/// }
/// ```
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultipartFileContentTypeValidator.class)
@Documented
public @interface MultipartFileContentType {

    /// @return the message template, interpolated with the accepted `types`
    String message() default "{jakarta.validation.constraints.MultipartFileContentType.message}";

    /// @return the accepted media types, wildcards allowed, parsed with `MediaType.parseMediaType`
    ///         when the validator is initialized
    String[] types();

    /// @return the validation groups the constraint belongs to
    Class<?>[] groups() default {};

    /// @return the payload attached to the constraint
    Class<? extends Payload>[] payload() default {};

    /// Validates a [MultipartFile] against [MultipartFileContentType].
    public static class MultipartFileContentTypeValidator implements ConstraintValidator<MultipartFileContentType, MultipartFile> {

        private List<MediaType> types;

        /// Parses the accepted types once.
        ///
        /// @param annotation the constraint
        /// @throws org.springframework.http.InvalidMediaTypeException when one of the types cannot be
        ///         parsed
        @Override
        public void initialize(MultipartFileContentType annotation) {
            this.types = Stream
                    .of(annotation.types())
                    .map(MediaType::parseMediaType)
                    .collect(Collectors.toList());
        }

        /// @param value the file to validate
        /// @param context the validation context
        /// @return true when the file is `null` or its content type is included in one of the
        ///         accepted types, false when it has no content type or matches none of them
        /// @throws org.springframework.http.InvalidMediaTypeException when the content type cannot be
        ///         parsed
        @Override
        public boolean isValid(MultipartFile value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            final var contentType = value.getContentType();
            if (contentType == null) {
                return false;
            }
            final MediaType mediaType = MediaType.parseMediaType(contentType);
            return types.stream().anyMatch(t -> t.includes(mediaType));
        }

    }
}
