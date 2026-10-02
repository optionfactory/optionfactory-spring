package net.optionfactory.spring.email;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class EmailSenderTest {

    @TempDir
    Path tmp;

    private static EmailSenderConfiguration placebo(Duration deadAfter) {
        return EmailSenderConfiguration
                .builder()
                .placebo(true)
                .host("example.com")
                .port(25)
                .protocol(EmailSenderConfiguration.Protocol.PLAIN)
                .deadAfter(deadAfter)
                .build();
    }

    private static Path spooledAnHourAgo(Path spool, String filename) throws IOException {
        final var eml = Files.createFile(spool.resolve(filename));
        Files.setLastModifiedTime(eml, FileTime.from(Instant.now().minus(Duration.ofHours(1))));
        return eml;
    }

    @Test
    public void sentEmailsAreMovedToSent() throws IOException {
        final Path sent = Path.of("target/test-sent/sent/");
        final Path spool = Path.of("target/test-sent/spool/");
        final var paths = EmailPaths.provide(spool, sent, null);
        final var sender = new EmailSender(paths, placebo(Duration.ofHours(1)));

        final var filename = String.format("%s.eml", UUID.randomUUID().toString());

        spool.resolve(filename).toFile().createNewFile();
        sender.processSpool();
        Assertions.assertTrue(Files.list(spool).noneMatch(p -> filename.equals(p.getFileName().toString())), "a sent email leaves the spool");
        Assertions.assertTrue(Files.list(sent).anyMatch(p -> filename.equals(p.getFileName().toString())), "a sent email is archived in the sent directory");
    }

    @Test
    public void sentEmailIsRemovedFromSpoolEvenIfArchivingFails() throws IOException {
        final Path sent = Path.of("target/test-move-fail/sent/");
        final Path spool = Path.of("target/test-move-fail/spool/");
        final var paths = EmailPaths.provide(spool, sent, null);
        final var sender = new EmailSender(paths, placebo(Duration.ofHours(1)));

        final var filename = String.format("%s.eml", UUID.randomUUID().toString());
        spool.resolve(filename).toFile().createNewFile();
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
    public void emailsAreMovedToDeadAfterDuration() throws IOException {
        final Path spool = Path.of("target/test-dead/spool/");
        final Path dead = Path.of("target/test-dead/dead/");
        final var paths = EmailPaths.provide(spool, null, dead);
        final var sender = new EmailSender(paths, placebo(Duration.ofMinutes(1)));

        final var filename = String.format("%s.eml", UUID.randomUUID().toString());
        spooledAnHourAgo(spool, filename);

        sender.processSpool();
        Assertions.assertTrue(Files.list(spool).noneMatch(p -> filename.equals(p.getFileName().toString())), "a dead email leaves the spool");
        Assertions.assertTrue(Files.list(dead).anyMatch(p -> filename.equals(p.getFileName().toString())), "a dead email is archived in the dead directory");
    }

    @Test
    public void archivedEmailIsNotLostOnNameCollision() throws IOException {
        final Path sent = Path.of("target/test-collision/sent/");
        final Path spool = Path.of("target/test-collision/spool/");
        final var paths = EmailPaths.provide(spool, sent, null);
        final var sender = new EmailSender(paths, placebo(Duration.ofHours(1)));

        final var filename = String.format("%s.eml", UUID.randomUUID().toString());
        Files.writeString(sent.resolve(filename), "stale");

        spool.resolve(filename).toFile().createNewFile();
        sender.processSpool();

        Assertions.assertTrue(Files.list(spool).noneMatch(p -> filename.equals(p.getFileName().toString())), "a sent email leaves the spool even when its name is already archived");
        Assertions.assertEquals("", Files.readString(sent.resolve(filename)), "the sent email replaces the stale archived one");
    }

    @Test
    public void deadEmailIsNotResentWhenMoveToDeadFails() throws IOException {
        final Path spool = Path.of("target/test-dead-fail/spool/");
        final Path sent = Path.of("target/test-dead-fail/sent/");
        final Path dead = Path.of("target/test-dead-fail/dead/");
        final var paths = EmailPaths.provide(spool, sent, dead);
        final var sender = new EmailSender(paths, placebo(Duration.ofMinutes(1)));

        final var filename = String.format("%s.eml", UUID.randomUUID().toString());
        spooledAnHourAgo(spool, filename);
        try (var stream = Files.walk(dead, 1)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }

        sender.processSpool();

        Assertions.assertFalse(Files.exists(sent.resolve(filename)),
                "dead email was re-sent (moved to sent) instead of being discarded");
        Assertions.assertFalse(Files.exists(spool.resolve(filename)),
                "dead email left in spool would be re-sent on the next tick");
    }

    @Test
    public void filesNotEndingInEmlAreLeftAlone() throws IOException {
        final var paths = EmailPaths.provide(tmp.resolve("spool"), tmp.resolve("sent"), tmp.resolve("dead"));
        final var sender = new EmailSender(paths, placebo(Duration.ofMinutes(1)));
        final var inProgress = spooledAnHourAgo(paths.spool(), "being-written.tmp");

        sender.processSpool();

        Assertions.assertTrue(Files.exists(inProgress), "an email still being marshalled is neither sent nor declared dead");
    }

    @Test
    public void sentEmailIsDeletedWithoutASentDirectory() throws IOException {
        final var paths = EmailPaths.provide(tmp.resolve("spool"), null, null);
        final var sender = new EmailSender(paths, placebo(Duration.ofHours(1)));
        final var eml = Files.createFile(paths.spool().resolve("a.eml"));

        sender.processSpool();

        Assertions.assertFalse(Files.exists(eml), "without a sent directory a sent email is deleted");
    }

    @Test
    public void deadEmailIsDeletedUnsentWithoutADeadDirectory() throws IOException {
        final var paths = EmailPaths.provide(tmp.resolve("spool"), tmp.resolve("sent"), null);
        final var sender = new EmailSender(paths, placebo(Duration.ofMinutes(1)));
        final var eml = spooledAnHourAgo(paths.spool(), "a.eml");

        sender.processSpool();

        Assertions.assertFalse(Files.exists(eml), "without a dead directory a dead email is deleted");
        Assertions.assertFalse(Files.exists(paths.sent().resolve("a.eml")), "a dead email is never sent");
    }

    @Test
    public void emailsNeverDieWithoutDeadAfter() throws IOException {
        final var paths = EmailPaths.provide(tmp.resolve("spool"), tmp.resolve("sent"), tmp.resolve("dead"));
        final var sender = new EmailSender(paths, placebo(null));
        spooledAnHourAgo(paths.spool(), "a.eml");

        sender.processSpool();

        Assertions.assertTrue(Files.exists(paths.sent().resolve("a.eml")), "without deadAfter an old email is still sent");
    }

    @Test
    public void anEmailThatFailsToSendStaysInTheSpoolForTheNextPass() throws IOException {
        final var paths = EmailPaths.provide(tmp.resolve("spool"), tmp.resolve("sent"), tmp.resolve("dead"));
        final var conf = EmailSenderConfiguration.builder()
                .host("smtp.example.com")
                .port(25)
                .protocol(EmailSenderConfiguration.Protocol.PLAIN)
                .deadAfter(Duration.ofHours(1))
                .build();
        final var sender = new EmailSender(paths, conf);
        final var unreadable = Files.createFile(paths.spool().resolve("a.eml"));
        Files.setPosixFilePermissions(unreadable, Set.of());
        Assumptions.assumeFalse(Files.isReadable(unreadable), "needs a user that file permissions apply to");

        sender.processSpool();

        Assertions.assertTrue(Files.exists(unreadable), "an email that could not be sent is kept in the spool to be retried");
        Assertions.assertFalse(Files.exists(paths.sent().resolve("a.eml")), "an email that could not be sent is not archived as sent");
    }

}
