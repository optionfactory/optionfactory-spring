package net.optionfactory.spring.validation.files;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

public class MultipartFileExtensionTest {

    public static record BeanWithMultipartFileExtension(@MultipartFileExtension(types = "svg") MultipartFile file) {

    }

    @Test
    public void canValidateInvalidExtension() {
        Validator v = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.ITALIAN, false))
                .buildValidatorFactory()
                .getValidator();
        final BeanWithMultipartFileExtension bean = new BeanWithMultipartFileExtension(ByteArrayMultipartFile.empty("a.png", "image/png"));
        Set<ConstraintViolation<BeanWithMultipartFileExtension>> result = v.validate(bean);
        Assertions.assertEquals("Tipo file non supportato, supportati: [svg]", result.iterator().next().getMessage());
    }

    @Test
    public void canValidateValidExtension() {
        Validator v = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.ITALIAN, false))
                .buildValidatorFactory().getValidator();
        final BeanWithMultipartFileExtension bean = new BeanWithMultipartFileExtension(ByteArrayMultipartFile.empty("a.svg", "image/svg"));
        Set<ConstraintViolation<BeanWithMultipartFileExtension>> result = v.validate(bean);
        Assertions.assertEquals(0, result.size());
    }

    @Test
    public void canValidateValidExtensionIngoringCase() {
        Validator v = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory().getValidator();
        final BeanWithMultipartFileExtension bean = new BeanWithMultipartFileExtension(ByteArrayMultipartFile.empty("a.SvG", "image/svg"));
        Set<ConstraintViolation<BeanWithMultipartFileExtension>> result = v.validate(bean);
        Assertions.assertEquals(0, result.size());
    }

    @Test
    public void filesWithEmptyExtensionAreInvalid() {
        Validator v = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory().getValidator();
        final BeanWithMultipartFileExtension bean = new BeanWithMultipartFileExtension(ByteArrayMultipartFile.empty("a.", "image/svg"));
        Set<ConstraintViolation<BeanWithMultipartFileExtension>> result = v.validate(bean);
        Assertions.assertEquals(1, result.size());
    }

    @Test
    public void filesWithoutExtensionsAreInvalid() {
        Validator v = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory().getValidator();
        final BeanWithMultipartFileExtension bean = new BeanWithMultipartFileExtension(ByteArrayMultipartFile.empty("a", "image/svg"));
        Set<ConstraintViolation<BeanWithMultipartFileExtension>> result = v.validate(bean);
        Assertions.assertEquals(1, result.size());
    }

}
