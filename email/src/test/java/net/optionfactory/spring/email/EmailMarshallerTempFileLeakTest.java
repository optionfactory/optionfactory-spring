package net.optionfactory.spring.email;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamSource;

public class EmailMarshallerTempFileLeakTest {

    @Test
    public void failedMarshalDoesNotLeaveTempFileInSpool() throws IOException {
        final Path spool = Path.of("target/test-marshal-leak/spool/");
        if (Files.exists(spool)) {
            try (var stream = Files.walk(spool)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        final var paths = EmailPaths.provide(spool, null, null);
        final var m = new EmailMarshaller();
        final InputStreamSource failing = () -> {
            throw new IOException("simulated attachment read failure");
        };
        final var em = EmailMessage.builder()
                .sender("test.sender@example.com", "Test sender")
                .recipient("test@example.com")
                .subject("test subject")
                .textBody("test")
                .attachments(AttachmentSource.of(failing, "broken.bin", "application/octet-stream"))
                .build();

        Assertions.assertThrows(EmailMarshaller.EmailMarshallingException.class, () -> m.marshalToSpool(em, paths, "leak"), "an unreadable attachment fails the marshalling");

        final long leftoverTemps = Files.list(spool)
                .filter(p -> p.getFileName().toString().endsWith(".tmp"))
                .count();
        Assertions.assertEquals(0, leftoverTemps, "a failed marshal leaves no temp file in the spool");
    }
}
