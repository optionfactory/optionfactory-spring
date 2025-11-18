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

public class EmailSender {

    private final Logger logger = LoggerFactory.getLogger(EmailSender.class);
    private final boolean placebo;
    private final EmailPaths paths;
    private final JavaMailSenderImpl javaMail;
    private final Optional<Duration> deadAfter;

    public EmailSender(EmailPaths paths, EmailSenderConfiguration conf) {
        this.paths = paths;
        this.placebo = conf.placebo();
        this.deadAfter = conf.deadAfter();
        this.javaMail = createJavaMail(conf);
    }

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
            logger.info(String.format("[send-emails] sent%s: %s", placebo ? "(placebo)" : "", eml.getFileName()));
            if (paths.sent() != null) {
                Path target = paths.sent().resolve(eml.getFileName());
                Files.move(eml, target, StandardCopyOption.ATOMIC_MOVE);
                logger.info(String.format("[send-emails] moved %s to sent directory", eml.getFileName()));
            } else {
                logger.info(String.format("[send-emails] deleted %s", eml.getFileName()));
                Files.delete(eml);
            }
        } catch (MessagingException ex) {
            logger.warn(String.format("[send-emails] failed to send email: %s", ex.getMessage()));
        } catch (IOException ex) {
            logger.error("[send-emails] failed to process sent email (move or remove)", ex);
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
                Files.move(eml, target, StandardCopyOption.ATOMIC_MOVE);
                logger.info(String.format("[send-emails] moved %s to dead directory", eml.getFileName()));

            } else {
                Files.delete(eml);
                logger.info(String.format("[send-emails] removed %s: dead", eml.getFileName()));
            }
        } catch (IOException ex) {
            logger.warn(String.format("[send-emails] failed to process dead email: %s", eml), ex);
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
            p.setProperty("mail.smtps.ssl.checkserveridentity", "false");
            p.setProperty("mail.smtps.ssl.trust", "*");
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
            p.setProperty("mail.smtp.ssl.checkserveridentity", "false");
            p.setProperty("mail.smtp.ssl.trust", "*");
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
