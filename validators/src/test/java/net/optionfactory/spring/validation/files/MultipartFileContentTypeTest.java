package net.optionfactory.spring.validation.files;

import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

public class MultipartFileContentTypeTest {

    private static final Validator VALIDATOR = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator(Set.of(), Locale.ENGLISH, false))
            .buildValidatorFactory()
            .getValidator();

    public record Upload(@MultipartFileContentType(types = {"image/*", "text/plain"}) MultipartFile file) {

    }

    private static List<String> violations(String contentType) {
        final var upload = new Upload(ByteArrayMultipartFile.empty("a.bin", contentType));
        return VALIDATOR.validate(upload).stream().map(cv -> cv.getMessage()).toList();
    }

    @Test
    public void aWildcardAdmitsEverySubtype() {
        Assertions.assertEquals(List.of(), violations("image/png"), "image/* must admit image/png");
    }

    @Test
    public void parametersOfTheDeclaredTypeAreIgnored() {
        Assertions.assertEquals(List.of(), violations("text/plain;charset=UTF-8"), "text/plain must admit text/plain with a charset");
    }

    @Test
    public void aTypeNotAcceptedIsReportedListingTheAcceptedOnes() {
        Assertions.assertEquals(List.of("Unsupported file type, supported: [image/*, text/plain]"), violations("application/pdf"), "a pdf must be rejected, the message listing the accepted types");
    }

    @Test
    public void aFileWithoutContentTypeIsInvalid() {
        Assertions.assertEquals(1, violations(null).size(), "a file whose client declared no content type must be rejected");
    }

    @Test
    public void aNullFileIsValid() {
        Assertions.assertEquals(Set.of(), VALIDATOR.validate(new Upload(null)), "a missing file is left to @NotNull");
    }

    @Test
    public void anUnparseableContentTypeFailsValidationWithAnException() {
        Assertions.assertThrows(ValidationException.class, () -> violations("garbage"), "a content type spring cannot parse must make validation throw rather than report a violation");
    }
}
