package net.optionfactory.spring.validation.files;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import net.optionfactory.spring.validation.files.MultipartFileContentType.MultipartFileContentTypeValidator;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultipartFileContentTypeValidator.class)
@Documented
public @interface MultipartFileContentType {

    String message() default "{javax.validation.constraints.MultipartFileContentType.message}";

    String[] types();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    public static class MultipartFileContentTypeValidator implements ConstraintValidator<MultipartFileContentType, MultipartFile> {

        private List<MediaType> types;

        @Override
        public void initialize(MultipartFileContentType annotation) {
            this.types = Stream
                    .of(annotation.types())
                    .map(MediaType::parseMediaType)
                    .collect(Collectors.toList());
        }

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
