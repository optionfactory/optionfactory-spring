package net.optionfactory.spring.validation.files;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import net.optionfactory.spring.validation.files.MultipartFileMaxSize.MultipartFileSizeValidator;
import org.springframework.web.multipart.MultipartFile;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultipartFileSizeValidator.class)
@Documented
public @interface MultipartFileMaxSize {

    String message() default "{javax.validation.constraints.MultipartFileMaxSize.message}";

    long value() default 1;

    Scale scale() default Scale.MiB;

    public enum Scale {
        B(1),
        KB(1000),
        KiB(1024),
        MB(1_000_000),
        MiB(1_048_576);
        public final int bytes;

        Scale(int bytes) {
            this.bytes = bytes;
        }
    }

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    public static class MultipartFileSizeValidator implements ConstraintValidator<MultipartFileMaxSize, MultipartFile> {

        private long maxSize;
        private Scale scale;

        @Override
        public void initialize(MultipartFileMaxSize annotation) {
            this.maxSize = annotation.value();
            this.scale = annotation.scale();
        }

        @Override
        public boolean isValid(MultipartFile value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return value.getSize() <= (maxSize * scale.bytes);
        }

    }
}
