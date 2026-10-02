package net.optionfactory.spring.validation.files;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

public class MultipartFilenameTest {

    private static final Validator VALIDATOR = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()
            .getValidator();

    public record DefaultPattern(@MultipartFilenamePattern MultipartFile file) {

    }

    public record PdfOnly(@MultipartFilenamePattern("[a-z]+\\.pdf") MultipartFile file) {

    }

    public record MaxLength(@MultipartFilenameMaxLength(10) MultipartFile file) {

    }

    public record DefaultMaxLength(@MultipartFilenameMaxLength MultipartFile file) {

    }

    private static MultipartFile named(String filename) {
        return ByteArrayMultipartFile.empty(filename, "application/octet-stream");
    }

    @Test
    public void theDefaultPatternAdmitsLettersDigitsDashesDotsUnderscoresAndSpaces() {
        Assertions.assertEquals(Set.of(), VALIDATOR.validate(new DefaultPattern(named("Report_2024-01 final.pdf"))), "an ordinary filename must be accepted");
    }

    @Test
    public void theDefaultPatternRejectsPathSeparators() {
        Assertions.assertEquals(1, VALIDATOR.validate(new DefaultPattern(named("../etc/passwd"))).size(), "a filename with path separators must be rejected");
    }

    @Test
    public void theDefaultPatternRejectsNonAsciiLetters() {
        Assertions.assertEquals(1, VALIDATOR.validate(new DefaultPattern(named("café.pdf"))).size(), "the default pattern is ascii-only: an accented letter must be rejected");
    }

    @Test
    public void aCustomPatternMustMatchTheWholeFilename() {
        Assertions.assertEquals(Set.of(), VALIDATOR.validate(new PdfOnly(named("report.pdf"))), "a filename matching the pattern must be accepted");
        Assertions.assertEquals(1, VALIDATOR.validate(new PdfOnly(named("report.pdf.exe"))).size(), "a filename only starting with a match must be rejected");
    }

    @Test
    public void aFilenameShorterThanTheLimitIsValid() {
        Assertions.assertEquals(Set.of(), VALIDATOR.validate(new MaxLength(named("abc.pdf"))), "a 7 character filename must be accepted with a limit of 10");
    }

    @Test
    public void aFilenameLongerThanTheLimitIsInvalid() {
        Assertions.assertEquals(1, VALIDATOR.validate(new MaxLength(named("abcdefghijk.pdf"))).size(), "a 15 character filename must be rejected with a limit of 10");
    }

    @Test
    public void aNullFileIsValidForBothConstraints() {
        Assertions.assertEquals(Set.of(), VALIDATOR.validate(new DefaultPattern(null)), "a missing file is left to @NotNull");
        Assertions.assertEquals(Set.of(), VALIDATOR.validate(new MaxLength(null)), "a missing file is left to @NotNull");
    }

    @Test
    public void theMaxLengthIsInclusive() {
        Assertions.assertEquals(Set.of(), VALIDATOR.validate(new MaxLength(named("abcdef.pdf"))), "a filename exactly as long as the max length must be accepted");
        Assertions.assertEquals(1, VALIDATOR.validate(new MaxLength(named("abcdefg.pdf"))).size(), "a filename one character longer than the max length must be rejected");
    }

    @Test
    public void theDefaultMaxLengthIs255() {
        Assertions.assertEquals(Set.of(), VALIDATOR.validate(new DefaultMaxLength(named("a".repeat(251) + ".pdf"))), "a 255 character filename must be accepted by default");
        Assertions.assertEquals(1, VALIDATOR.validate(new DefaultMaxLength(named("a".repeat(252) + ".pdf"))).size(), "a 256 character filename must be rejected by default");
    }
}
