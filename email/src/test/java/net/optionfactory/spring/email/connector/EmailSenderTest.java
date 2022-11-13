package net.optionfactory.spring.email.connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import net.optionfactory.spring.email.EmailPaths;
import org.junit.Assert;
import org.junit.Test;

public class EmailSenderTest {

    @Test
    public void sentEmailsAreMovedToSent() throws IOException {
        final Path sent = Path.of("target/test-sent/sent/");
        final Path spool = Path.of("target/test-sent/spool/");
        final var paths = EmailPaths.provide(spool, sent, null);
        final EmailConnector connector = null;
        final var sender = new EmailSender(true, paths, connector, Duration.ofHours(1));

        String filename = String.format("%s.eml", UUID.randomUUID().toString());

        spool.resolve(filename).toFile().createNewFile();
        sender.processSpool();
        Assert.assertTrue(Files.list(spool).noneMatch(p -> filename.equals(p.getFileName().toString())));
        Assert.assertTrue(Files.list(sent).anyMatch(p -> filename.equals(p.getFileName().toString())));
    }

    @Test
    public void emailsAreMovedToDeadAfterDuration() throws IOException, InterruptedException {
        final Path spool = Path.of("target/test-dead/spool/");
        final Path dead = Path.of("target/test-dead/dead/");
        final var paths = EmailPaths.provide(spool, null, dead);
        final EmailConnector connector = null;
        final var sender = new EmailSender(true, paths, connector, Duration.ofMillis(100));

        String filename = String.format("%s.eml", UUID.randomUUID().toString());

        spool.resolve(filename).toFile().createNewFile();
        Thread.sleep(101);
        sender.processSpool();
        Assert.assertTrue(Files.list(spool).noneMatch(p -> filename.equals(p.getFileName().toString())));
        Assert.assertTrue(Files.list(dead).anyMatch(p -> filename.equals(p.getFileName().toString())));
    }

}
