package net.optionfactory.spring.validation.files;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Set;
import net.optionfactory.spring.validation.files.MultipartFileMaxSize.Scale;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

public class MultipartFileSizeTest {

    private static final Validator VALIDATOR = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.ITALIAN, false))
            .buildValidatorFactory()
            .getValidator();

    public record BeanWithMultipartFileSize(@MultipartFileMaxSize(value = 1) MultipartFile file) {

    }

    public record DecimalKilobytes(@MultipartFileMaxSize(value = 2, scale = Scale.KB) MultipartFile file) {

    }

    private static MultipartFile sized(int bytes) {
        return new ByteArrayMultipartFile("a.png", "image/png", new byte[bytes]);
    }

    @Test
    public void aFileOverTheLimitIsRejectedNamingTheLimit() {
        final var result = VALIDATOR.validate(new BeanWithMultipartFileSize(sized(1024 * 1024 + 1)));
        Assertions.assertEquals("File troppo grande, dimensione massima: 1MiB", result.iterator().next().getMessage(), "a file one byte over the default 1MiB must be rejected, the message naming the limit");
    }

    @Test
    public void sizeEqualsToThresholdIsValid() {
        final var result = VALIDATOR.validate(new BeanWithMultipartFileSize(sized(1024 * 1024)));
        Assertions.assertEquals(0, result.size(), "the limit is inclusive: a file of exactly 1MiB must be accepted");
    }

    @Test
    public void decimalScalesCountInPowersOfTen() {
        Assertions.assertEquals(0, VALIDATOR.validate(new DecimalKilobytes(sized(2000))).size(), "2KB are 2000 bytes: a 2000 bytes file must be accepted");
        Assertions.assertEquals(1, VALIDATOR.validate(new DecimalKilobytes(sized(2001))).size(), "2KB are 2000 bytes: a 2001 bytes file must be rejected");
    }

    @Test
    public void aNullFileIsValid() {
        Assertions.assertEquals(0, VALIDATOR.validate(new BeanWithMultipartFileSize(null)).size(), "a missing file is left to @NotNull");
    }
}
