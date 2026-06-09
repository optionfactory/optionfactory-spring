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

@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = MultipartMaxFilenameLengthValidator.class)
public @interface MultipartFilenameMaxLength {

    long value() default 256;

    String message() default "{jakarta.validation.constraints.MultipartFilenameMaxLength.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    public static class MultipartMaxFilenameLengthValidator implements ConstraintValidator<MultipartFilenameMaxLength, MultipartFile> {

        private MultipartFilenameMaxLength annotation;

        @Override
        public void initialize(MultipartFilenameMaxLength constraintAnnotation) {
            this.annotation = constraintAnnotation;
        }

        @Override
        public boolean isValid(MultipartFile value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            final var filename = value.getOriginalFilename();
            return !(filename.length() >= annotation.value());
        }
    }
}
