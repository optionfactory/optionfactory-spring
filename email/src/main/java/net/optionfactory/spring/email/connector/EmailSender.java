package net.optionfactory.spring.email.connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import net.optionfactory.spring.email.EmailPaths;
import net.optionfactory.spring.email.connector.EmailConnector.EmailSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.lang.Nullable;

public class EmailSender {

    private final Logger logger = LoggerFactory.getLogger(EmailSender.class);
    private final boolean placebo;
    private final EmailPaths paths;
    private final EmailConnector emails;
    private final Duration deadAfter;

    public EmailSender(boolean placebo, EmailPaths paths, EmailConnector emails, @Nullable Duration deadAfter) {
        this.placebo = placebo;
        this.paths = paths;
        this.emails = emails;
        this.deadAfter = deadAfter;
    }

    public void processSpool() {
        try {
            try (final Stream<Path> emls = Files.walk(paths.spool, 1)) {
                final Instant now = Instant.now();
                emls.filter(p -> Files.isRegularFile(p))
                        .filter(p -> p.getFileName().toString().endsWith(".eml"))
                        .filter(p -> isDead(p, now))
                        .forEach(this::handleDead);
            }
            try (final Stream<Path> emls = Files.walk(paths.spool, 1)) {
                emls.filter(p -> Files.isRegularFile(p))
                        .filter(p -> p.getFileName().toString().endsWith(".eml"))
                        .forEach(this::trySendSpooledEmail);
            }
        } catch (IOException ex) {
            throw new EmailSendException(ex.getMessage(), ex);
        }

    }

    private void trySendSpooledEmail(Path eml) {
        try {
            if (!placebo) {
                emails.send(new PathResource(eml));
            }
            logger.info(String.format("[send-emails] sent%s: %s", placebo ? "" : "(placebo)", eml.getFileName()));
            if (paths.sent != null) {
                Path target = paths.sent.resolve(eml.getFileName());
                Files.move(eml, target, StandardCopyOption.ATOMIC_MOVE);
                logger.info(String.format("[send-emails] moved %s to sent directory", eml.getFileName()));
            } else {
                logger.info(String.format("[send-emails] deleted %s", eml.getFileName()));
                Files.delete(eml);
            }
        } catch (EmailSendException ex) {
            logger.warn(String.format("[send-emails] failed to send email: %s", ex.getMessage()));
        } catch (IOException ex) {
            logger.error("[send-emails] failed to process sent email (move or remove)", ex);
        }
    }

    private boolean isDead(Path eml, Instant now) {
        if (deadAfter == null) {
            return false;
        }
        try {
            final var createdAt = Files.readAttributes(eml, BasicFileAttributes.class).creationTime().toInstant();
            final var deadAt = createdAt.plus(deadAfter);
            final var isDead = deadAt.isBefore(now);
            return isDead;
        } catch (IOException ex) {
            logger.warn(String.format("[send-emails] failed to check dead email: %s", eml), ex);
            return false;
        }
    }

    private void handleDead(Path eml) {
        try {
            if (paths.dead != null) {
                Path target = paths.dead.resolve(eml.getFileName());
                Files.move(eml, target, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.delete(eml);
            }
        } catch (IOException ex) {
            logger.warn(String.format("[send-emails] failed to process dead email: %s", eml), ex);
        }
    }

}
