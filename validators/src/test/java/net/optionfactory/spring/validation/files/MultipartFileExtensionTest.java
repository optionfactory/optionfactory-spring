package net.optionfactory.spring.validation.files;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.multipart.MultipartFile;

public class MultipartFileExtensionTest {

    public static class BeanWithMultipartFileExtension {

        @MultipartFileExtension(types = "svg")
        public MultipartFile file;

    }

    @Test
    public void canValidateInvalidExtension() {
        Validator v = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.ITALIAN, false))
                .buildValidatorFactory()
                .getValidator();
        final BeanWithMultipartFileExtension bean = new BeanWithMultipartFileExtension();
        bean.file = new ByteArrayMultipartFile("a.png", "image/png", new byte[0]);
        Set<ConstraintViolation<BeanWithMultipartFileExtension>> result = v.validate(bean);
        Assert.assertEquals("Tipo file non supportato, supportati: [svg]", result.iterator().next().getMessage());
    }

    @Test
    public void canValidateValidExtension() {
        Validator v = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.ITALIAN, false))
                .buildValidatorFactory().getValidator();
        final BeanWithMultipartFileExtension bean = new BeanWithMultipartFileExtension();
        bean.file = new ByteArrayMultipartFile("a.svg", "image/svg", new byte[0]);
        Set<ConstraintViolation<BeanWithMultipartFileExtension>> result = v.validate(bean);
        Assert.assertEquals(0, result.size());
    }

    @Test
    public void canValidateValidExtensionIngoringCase() {
        Validator v = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory().getValidator();
        final BeanWithMultipartFileExtension bean = new BeanWithMultipartFileExtension();
        bean.file = new ByteArrayMultipartFile("a.SvG", "image/svg", new byte[0]);
        Set<ConstraintViolation<BeanWithMultipartFileExtension>> result = v.validate(bean);
        Assert.assertEquals(0, result.size());
    }

    @Test
    public void filesWithEmptyExtensionAreInvalid() {
        Validator v = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory().getValidator();
        final BeanWithMultipartFileExtension bean = new BeanWithMultipartFileExtension();
        bean.file = new ByteArrayMultipartFile("a.", "image/svg", new byte[0]);
        Set<ConstraintViolation<BeanWithMultipartFileExtension>> result = v.validate(bean);
        Assert.assertEquals(1, result.size());
    }

    @Test
    public void filesWithoutExtensionsAreInvalid() {
        Validator v = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory().getValidator();
        final BeanWithMultipartFileExtension bean = new BeanWithMultipartFileExtension();
        bean.file = new ByteArrayMultipartFile("a", "image/svg", new byte[0]);
        Set<ConstraintViolation<BeanWithMultipartFileExtension>> result = v.validate(bean);
        Assert.assertEquals(1, result.size());
    }

}
