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

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultipartFileExtensionValidator.class)
@Documented
public @interface MultipartFileExtension {

    String message() default "{jakarta.validation.constraints.MultipartFileExtension.message}";

    String[] types();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    public static class MultipartFileExtensionValidator implements ConstraintValidator<MultipartFileExtension, MultipartFile> {

        private String[] types;

        @Override
        public void initialize(MultipartFileExtension annotation) {
            this.types = annotation.types();
        }

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
