package net.optionfactory.spring.email.connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.optionfactory.spring.email.connector.EmailConnector.EmailSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.util.Assert;

public class EmailSender {

    private final Logger logger = LoggerFactory.getLogger(EmailSender.class);
    private final boolean placebo;
    private final Path spoolDir;
    private final EmailConnector emails;

    public EmailSender(boolean placebo, Path spoolDir, EmailConnector emails) {
        Assert.isTrue(Files.isDirectory(spoolDir), "spoolDir must be a directory");
        Assert.isTrue(Files.isWritable(spoolDir), "spoolDir must be writable");
        this.placebo = placebo;
        this.spoolDir = spoolDir;
        this.emails = emails;
    }

    public void sendSpooledEmails() {
        if (placebo) {
            return;
        }
        try (final Stream<Path> paths = Files.walk(spoolDir, 1)) {
            paths.filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.getFileName().toString().endsWith(".eml"))
                    .forEach(this::trySendSpooledEmail);
        } catch (IOException ex) {
            throw new EmailSendException(ex.getMessage(), ex);
        }

    }

    private void trySendSpooledEmail(Path eml) {
        try {
            emails.send(new PathResource(eml));
            logger.info(String.format("[send-emails] sent: %s", eml.getFileName()));
            Files.delete(eml);
        } catch (EmailSendException ex) {
            logger.warn(String.format("[send-emails] failed to sendemail: %s", ex.getMessage()));
        } catch (IOException ex) {
            logger.error("[send-emails] failed to remove sent email", ex);
        }
    }
}
