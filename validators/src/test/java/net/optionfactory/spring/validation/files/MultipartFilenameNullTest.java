package net.optionfactory.spring.validation.files;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

public class MultipartFilenameNullTest {

    public static record BeanWithMaxLength(@MultipartFilenameMaxLength(value = 10) MultipartFile file) {
    }

    public static record BeanWithPattern(@MultipartFilenamePattern MultipartFile file) {
    }

    private static Validator validator() {
        return Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory().getValidator();
    }

    @Test
    public void maxLengthDoesNotNpeOnNullFilename() {
        final var bean = new BeanWithMaxLength(new ByteArrayMultipartFile(null, "text/plain", new byte[0]));
        final Set<ConstraintViolation<BeanWithMaxLength>> result = validator().validate(bean);
        Assertions.assertEquals(1, result.size(), "a null filename must fail validation, not throw");
    }

    @Test
    public void patternDoesNotNpeOnNullFilename() {
        final var bean = new BeanWithPattern(new ByteArrayMultipartFile(null, "text/plain", new byte[0]));
        final Set<ConstraintViolation<BeanWithPattern>> result = validator().validate(bean);
        Assertions.assertEquals(1, result.size(), "a null filename must fail validation, not throw");
    }
}
