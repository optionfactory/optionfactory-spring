package net.optionfactory.spring.validation.files;

import java.util.Locale;
import java.util.Set;
import javax.validation.Validation;
import javax.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.multipart.MultipartFile;

public class MultipartFileSizeTest {

    public static class BeanWithMultipartFileSize {

        @MultipartFileMaxSize(value = 1)
        public MultipartFile file;

    }

    @Test
    public void canValidateInvalidExtension() {
        Validator v = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.ITALIAN, false))
                .buildValidatorFactory().getValidator();

        final var bean = new BeanWithMultipartFileSize();
        bean.file = new ByteArrayMultipartFile("a.png", "image/png", new byte[1024 * 1024 + 1]);
        final var result = v.validate(bean);

        Assert.assertEquals("File troppo grande, dimensione massima: 1MiB", result.iterator().next().getMessage());
    }

    @Test
    public void sizeEqualsToThresholdIsValid() {
        Validator v = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.ITALIAN, false))
                .buildValidatorFactory().getValidator();

        final var bean = new BeanWithMultipartFileSize();
        bean.file = new ByteArrayMultipartFile("a.png", "image/png", new byte[1024 * 1024]);
        final var result = v.validate(bean);
        Assert.assertEquals(0, result.size());
    }

}
