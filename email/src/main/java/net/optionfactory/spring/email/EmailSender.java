package net.optionfactory.spring.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import net.optionfactory.spring.email.EmailSenderConfiguration.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/// Delivers the emails found in the spool directory of an [EmailPaths] to an smtp server.
///
/// The spool is a plain directory of `.eml` files, written by [EmailMarshaller#marshalToSpool], so
/// that producing an email never waits on, nor fails because of, the smtp server. Each call to
/// [#processSpool()] makes one pass over it:
///
/// 1. emails older than `deadAfter` are given up on: moved to the `dead` directory, or deleted
///    when there is none, without being sent;
/// 2. every remaining email is sent, then moved to the `sent` directory, or deleted when there is
///    none.
///
/// An email that fails to send is logged at WARN and left in the spool, to be retried at the next
/// pass until it is sent or dies. Files not ending in `.eml`, such as the `.tmp` files of an email
/// still being written, are ignored.
///
/// An archiving failure never causes a second delivery: an email that was sent (or that died) but
/// cannot be moved to its archive directory is deleted from the spool, and logged at ERROR, rather
/// than left there to be sent again.
///
/// Usually driven by a [ScheduledEmailSender]:
///
/// ```java
/// final var paths = EmailPaths.provide(Path.of("/var/spool/app/emails"), Path.of("/var/spool/app/sent"), null);
/// final var sender = new EmailSender(paths, conf);
/// new ScheduledEmailSender(sender, applicationContext, taskScheduler, Duration.ofSeconds(10), Duration.ofMinutes(1));
/// ```
public class EmailSender {

    private final Logger logger = LoggerFactory.getLogger(EmailSender.class);
    private final boolean placebo;
    private final EmailPaths paths;
    private final JavaMailSenderImpl javaMail;
    private final Optional<Duration> deadAfter;

    /// No connection is opened here.
    ///
    /// @param paths the spool to read and the directories to archive to
    /// @param conf the smtp server and the retry policy
    public EmailSender(EmailPaths paths, EmailSenderConfiguration conf) {
        this.paths = paths;
        this.placebo = conf.placebo();
        this.deadAfter = conf.deadAfter();
        this.javaMail = createJavaMail(conf);
    }

    /// Makes one pass over the spool, as described in the class documentation. Failures to send
    /// or archive a single email are logged and never thrown.
    ///
    /// Assumes a single EmailSender per machine and a spool directory owned by this process
    /// alone: nothing here claims files atomically, so concurrent invocations would race and
    /// double-send. The scheduled path is serialized by `ScheduledEmailSender`'s lock; any other
    /// caller must arrange its own mutual exclusion.
    ///
    /// @throws java.io.UncheckedIOException when the spool directory cannot be listed
    public void processSpool() {
        try {
            try (final Stream<Path> emls = Files.walk(paths.spool(), 1)) {
                final Instant now = Instant.now();
                emls.filter(p -> Files.isRegularFile(p))
                        .filter(p -> p.getFileName().toString().endsWith(".eml"))
                        .filter(p -> isDead(p, now))
                        .forEach(this::handleDead);
            }
            try (final Stream<Path> emls = Files.walk(paths.spool(), 1)) {
                emls.filter(p -> Files.isRegularFile(p))
                        .filter(p -> p.getFileName().toString().endsWith(".eml"))
                        .forEach(this::trySendSpooledEmail);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex.getMessage(), ex);
        }

    }

    private void trySendSpooledEmail(Path eml) {
        try {
            if (!placebo) {
                try (InputStream emlStream = new FileSystemResource(eml).getInputStream()) {
                    final MimeMessage message = new MimeMessage(javaMail.getSession(), emlStream);
                    javaMail.send(message);
                }
            }
        } catch (MessagingException | RuntimeException | IOException ex) {
            logger.warn(String.format("[send-emails] failed to send email: %s", eml.getFileName()), ex);
            return;
        }
        logger.info(String.format("[send-emails] sent%s: %s", placebo ? "(placebo)" : "", eml.getFileName()));
        try {
            if (paths.sent() != null) {
                Path target = paths.sent().resolve(eml.getFileName());
                Files.move(eml, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                logger.info(String.format("[send-emails] moved %s to sent directory", eml.getFileName()));
            } else {
                Files.delete(eml);
                logger.info(String.format("[send-emails] deleted %s", eml.getFileName()));
            }
        } catch (IOException ex) {
            logger.error(String.format("[send-emails] sent but failed to archive %s, deleting from spool to prevent re-send", eml.getFileName()), ex);
            try {
                Files.deleteIfExists(eml);
            } catch (IOException ex2) {
                logger.error(String.format("[send-emails] failed to delete %s after archive failure", eml.getFileName()), ex2);
            }
        }
    }

    private boolean isDead(Path eml, Instant now) {
        if (deadAfter.isEmpty()) {
            return false;
        }
        try {
            final var createdAt = Files.readAttributes(eml, BasicFileAttributes.class).lastModifiedTime().toInstant();
            final var deadAt = createdAt.plus(deadAfter.get());
            final var isDead = deadAt.isBefore(now);
            return isDead;
        } catch (IOException ex) {
            logger.warn(String.format("[send-emails] failed to check dead email: %s", eml), ex);
            return false;
        }
    }

    private void handleDead(Path eml) {
        try {
            if (paths.dead() != null) {
                Path target = paths.dead().resolve(eml.getFileName());
                Files.move(eml, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                logger.info(String.format("[send-emails] moved %s to dead directory", eml.getFileName()));

            } else {
                Files.delete(eml);
                logger.info(String.format("[send-emails] removed %s: dead", eml.getFileName()));
            }
        } catch (IOException ex) {
            logger.error(String.format("[send-emails] failed to archive dead email %s, deleting from spool to prevent re-send", eml.getFileName()), ex);
            try {
                Files.deleteIfExists(eml);
            } catch (IOException ex2) {
                logger.error(String.format("[send-emails] failed to delete dead %s after archive failure", eml.getFileName()), ex2);
            }
        }
    }

    private static JavaMailSenderImpl createJavaMail(EmailSenderConfiguration conf) {
        final var javaMail = new JavaMailSenderImpl();
        javaMail.setHost(conf.host());
        javaMail.setPort(conf.port());
        conf.username().ifPresent(username -> {
            javaMail.setUsername(username);
            if (conf.password().isPresent()) {
                javaMail.setPassword(conf.password().get());
            }
        });
        javaMail.setJavaMailProperties(confToProperties(conf));
        return javaMail;
    }

    private static Properties confToProperties(EmailSenderConfiguration conf) {
        final var p = new Properties();
        if (conf.protocol() == Protocol.TLS) {
            p.setProperty("mail.transport.protocol", "smtps");
            p.setProperty("mail.smtps.connectiontimeout", Long.toString(conf.connectionTimeout().toMillis()));
            p.setProperty("mail.smtps.timeout", Long.toString(conf.readTimeout().toMillis()));
            p.setProperty("mail.smtps.writetimeout", Long.toString(conf.writeTimeout().toMillis()));
            p.setProperty("mail.smtps.socketFactory.fallback", "false");
            p.setProperty("mail.smtps.ssl.protocols", "TLSv1.2 TLSv1.3");
            if (conf.checkServerIdentity()) {
                p.setProperty("mail.smtps.ssl.checkserveridentity", "true");
            } else {
                p.setProperty("mail.smtps.ssl.checkserveridentity", "false");
                p.setProperty("mail.smtps.ssl.trust", "*");
            }
            conf.sslSocketFactory().ifPresentOrElse(sf -> {
                p.put("mail.smtps.socketFactory", sf);
            }, () -> {
                p.setProperty("mail.smtps.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            });
            conf.username().ifPresent(username -> {
                p.setProperty("mail.smtps.auth", "true");
            });
            return p;
        }
        p.setProperty("mail.smtp.connectiontimeout", Long.toString(conf.connectionTimeout().toMillis()));
        p.setProperty("mail.smtp.timeout", Long.toString(conf.readTimeout().toMillis()));
        p.setProperty("mail.smtp.writetimeout", Long.toString(conf.writeTimeout().toMillis()));
        conf.username().ifPresent(username -> {
            p.setProperty("mail.smtp.auth", "true");
        });
        if (conf.protocol() == Protocol.START_TLS_REQUIRED || conf.protocol() == Protocol.START_TLS_SUPPORTED) {
            p.setProperty("mail.smtp.starttls.enable", "true");
            p.setProperty("mail.smtp.socketFactory.fallback", "false");
            p.setProperty("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
            if (conf.checkServerIdentity()) {
                p.setProperty("mail.smtp.ssl.checkserveridentity", "true");
            } else {
                p.setProperty("mail.smtp.ssl.checkserveridentity", "false");
                p.setProperty("mail.smtp.ssl.trust", "*");
            }
            if (conf.protocol() == Protocol.START_TLS_REQUIRED) {
                p.setProperty("mail.smtp.starttls.required", "true");
            }
            conf.sslSocketFactory().ifPresentOrElse(sf -> {
                p.put("mail.smtp.ssl.socketFactory", sf);
            }, () -> {
                p.setProperty("mail.smtp.ssl.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            });
        }
        return p;
    }

}
