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

@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = MultipartFilenamePatternValidator.class)
public @interface MultipartFilenamePattern {

    String value() default "^[\\w\\-. ]+$";

    String message() default "{jakarta.validation.constraints.MultipartFilenamePattern.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    public static class MultipartFilenamePatternValidator implements ConstraintValidator<MultipartFilenamePattern, MultipartFile> {

        private Pattern pattern;

        @Override
        public void initialize(MultipartFilenamePattern constraintAnnotation) {
            this.pattern = Pattern.compile(constraintAnnotation.value());
        }

        @Override
        public boolean isValid(MultipartFile value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return pattern.matcher(value.getOriginalFilename()).matches();
        }
    }
}
