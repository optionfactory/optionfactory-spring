package net.optionfactory.spring.email;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EmailSenderTest {

    @Test
    public void sentEmailsAreMovedToSent() throws IOException {
        final Path sent = Path.of("target/test-sent/sent/");
        final Path spool = Path.of("target/test-sent/spool/");
        final var paths = EmailPaths.provide(spool, sent, null);
        final var configuration = EmailSenderConfiguration
                .builder()
                .placebo(true)
                .host("example.com")
                .port(25)
                .protocol(EmailSenderConfiguration.Protocol.PLAIN)
                .deadAfter(Duration.ofHours(1))
                .build();
        final var sender = new EmailSender(paths, configuration);

        final var filename = String.format("%s.eml", UUID.randomUUID().toString());

        spool.resolve(filename).toFile().createNewFile();
        sender.processSpool();
        Assertions.assertTrue(Files.list(spool).noneMatch(p -> filename.equals(p.getFileName().toString())));
        Assertions.assertTrue(Files.list(sent).anyMatch(p -> filename.equals(p.getFileName().toString())));
    }

    @Test
    public void sentEmailIsRemovedFromSpoolEvenIfArchivingFails() throws IOException {
        final Path sent = Path.of("target/test-move-fail/sent/");
        final Path spool = Path.of("target/test-move-fail/spool/");
        final var paths = EmailPaths.provide(spool, sent, null);
        final var configuration = EmailSenderConfiguration
                .builder()
                .placebo(true)
                .host("example.com")
                .port(25)
                .protocol(EmailSenderConfiguration.Protocol.PLAIN)
                .deadAfter(Duration.ofHours(1))
                .build();
        final var sender = new EmailSender(paths, configuration);

        final var filename = String.format("%s.eml", UUID.randomUUID().toString());
        spool.resolve(filename).toFile().createNewFile();
        // remove the sent directory so the post-send move fails (simulating a full/permission/IO failure).
        // the email was already "sent" (placebo), so leaving it in spool would cause a duplicate re-send on the next tick.
        try (var stream = Files.walk(sent, 1)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }

        sender.processSpool();

        Assertions.assertTrue(
                Files.list(spool).noneMatch(p -> filename.equals(p.getFileName().toString())),
                "email left in spool after a successful send would be re-sent on the next tick"
        );
    }

    @Test
    public void emailsAreMovedToDeadAfterDuration() throws IOException, InterruptedException {
        final Path spool = Path.of("target/test-dead/spool/");
        final Path dead = Path.of("target/test-dead/dead/");
        final var paths = EmailPaths.provide(spool, null, dead);
        final var configuration = EmailSenderConfiguration
                .builder()
                .placebo(true)
                .host("example.com")
                .port(25)
                .protocol(EmailSenderConfiguration.Protocol.PLAIN)
                .deadAfter(Duration.ofMillis(100))
                .build();
        
        final var sender = new EmailSender(paths, configuration);

        String filename = String.format("%s.eml", UUID.randomUUID().toString());

        spool.resolve(filename).toFile().createNewFile();
        Thread.sleep(101);
        sender.processSpool();
        Assertions.assertTrue(Files.list(spool).noneMatch(p -> filename.equals(p.getFileName().toString())));
        Assertions.assertTrue(Files.list(dead).anyMatch(p -> filename.equals(p.getFileName().toString())));
    }

}
