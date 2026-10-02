package net.optionfactory.spring.validation.files;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

public class MultipartFileExtensionTest {

    private static final Validator VALIDATOR = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.ITALIAN, false))
            .buildValidatorFactory()
            .getValidator();

    public static record BeanWithMultipartFileExtension(@MultipartFileExtension(types = "svg") MultipartFile file) {

    }

    public static record BeanWithArchiveExtensions(@MultipartFileExtension(types = {"zip", "gz"}) MultipartFile file) {

    }

    @Test
    public void canValidateInvalidExtension() {
        final var bean = new BeanWithMultipartFileExtension(ByteArrayMultipartFile.empty("a.png", "image/png"));
        final var result = VALIDATOR.validate(bean);
        Assertions.assertEquals("Tipo file non supportato, supportati: [svg]", result.iterator().next().getMessage(), "a png must be rejected, the message listing the accepted extensions");
    }

    @Test
    public void canValidateValidExtension() {
        final var bean = new BeanWithMultipartFileExtension(ByteArrayMultipartFile.empty("a.svg", "image/svg"));
        Assertions.assertEquals(0, VALIDATOR.validate(bean).size(), "an svg must be accepted");
    }

    @Test
    public void canValidateValidExtensionIngoringCase() {
        final var bean = new BeanWithMultipartFileExtension(ByteArrayMultipartFile.empty("a.SvG", "image/svg"));
        Assertions.assertEquals(0, VALIDATOR.validate(bean).size(), "extensions must be compared ignoring case");
    }

    @Test
    public void filesWithEmptyExtensionAreInvalid() {
        final var bean = new BeanWithMultipartFileExtension(ByteArrayMultipartFile.empty("a.", "image/svg"));
        Assertions.assertEquals(1, VALIDATOR.validate(bean).size(), "a filename ending with a dot has an empty extension and must be rejected");
    }

    @Test
    public void filesWithoutExtensionsAreInvalid() {
        final var bean = new BeanWithMultipartFileExtension(ByteArrayMultipartFile.empty("a", "image/svg"));
        Assertions.assertEquals(1, VALIDATOR.validate(bean).size(), "a filename without a dot must be rejected");
    }

    @Test
    public void onlyWhatFollowsTheLastDotIsTheExtension() {
        Assertions.assertEquals(0, VALIDATOR.validate(new BeanWithArchiveExtensions(ByteArrayMultipartFile.empty("backup.tar.gz", "application/gzip"))).size(), "archive.tar.gz has the extension gz, which is accepted");
        Assertions.assertEquals(1, VALIDATOR.validate(new BeanWithArchiveExtensions(ByteArrayMultipartFile.empty("backup.zip.exe", "application/octet-stream"))).size(), "backup.zip.exe has the extension exe, which is not accepted");
    }

    @Test
    public void aNullFileIsValid() {
        Assertions.assertEquals(0, VALIDATOR.validate(new BeanWithMultipartFileExtension(null)).size(), "a missing file is left to @NotNull");
    }

    @Test
    public void aFileWithoutOriginalFilenameIsInvalid() {
        final var bean = new BeanWithMultipartFileExtension(ByteArrayMultipartFile.empty(null, "image/svg"));
        Assertions.assertEquals(1, VALIDATOR.validate(bean).size(), "a file without an original filename has no extension and must be rejected, not throw");
    }
}
