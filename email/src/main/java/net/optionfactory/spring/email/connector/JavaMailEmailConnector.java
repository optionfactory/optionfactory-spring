package net.optionfactory.spring.email.connector;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

public class JavaMailEmailConnector implements EmailConnector {

    public static class Configuration {

        public int connectionTimeoutMs;
        public int readTimeoutMs;
        public int writeTimeoutMs;

        public String host;
        public Optional<SSLSocketFactory> sslSocketFactory;

        public int port;
        public boolean startTlsEnabled;
        public boolean startTlsRequired;
        public boolean useSsl;

        public Optional<String> username;
        public Optional<String> password;

    }

    private final JavaMailSenderImpl mailSender;

    public JavaMailEmailConnector(Configuration conf) {
        var sender = new JavaMailSenderImpl();
        sender.setHost(conf.host);
        sender.setPort(conf.port);
        conf.username.ifPresent(username -> {
            sender.setUsername(username);
            if (conf.password.isPresent()) {
                sender.setPassword(conf.password.get());
            }
        });
        sender.setJavaMailProperties(confToProperties(conf));
        this.mailSender = sender;
    }

    @Override
    public void send(InputStreamSource emlSource) {
        try (InputStream emlStream = emlSource.getInputStream()) {
            final MimeMessage message = new MimeMessage(mailSender.getSession(), emlStream);
            mailSender.send(message);
        } catch (Exception ex) {
            throw new EmailSendException(ex.getMessage(), ex);
        }
    }

    private static Properties confToProperties(Configuration conf) {
        final var p = new Properties();
        if (conf.useSsl) {
            p.setProperty("mail.transport.protocol", "smtps");
            p.setProperty("mail.smtps.connectiontimeout", Integer.toString(conf.connectionTimeoutMs));
            p.setProperty("mail.smtps.timeout", Integer.toString(conf.readTimeoutMs));
            p.setProperty("mail.smtps.writetimeout", Integer.toString(conf.writeTimeoutMs));
            p.setProperty("mail.smtps.socketFactory.fallback", "false");
            p.setProperty("mail.smtps.ssl.protocols", "TLSv1.2 TLSv1.3");
            p.setProperty("mail.smtps.ssl.checkserveridentity", "false");
            p.setProperty("mail.smtps.ssl.trust", "*");
            conf.sslSocketFactory.ifPresentOrElse(sf -> {
                p.put("mail.smtps.socketFactory", sf);
            }, () -> {
                p.setProperty("mail.smtps.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            });
            conf.username.ifPresent(username -> {
                p.setProperty("mail.smtps.auth", "true");
            });
            return p;
        }
        p.setProperty("mail.smtp.connectiontimeout", Integer.toString(conf.connectionTimeoutMs));
        p.setProperty("mail.smtp.timeout", Integer.toString(conf.readTimeoutMs));
        p.setProperty("mail.smtp.writetimeout", Integer.toString(conf.writeTimeoutMs));
        conf.username.ifPresent(username -> {
            p.setProperty("mail.smtp.auth", "true");
        });
        if (conf.startTlsEnabled) {
            p.setProperty("mail.smtp.starttls.enable", "true");
            p.setProperty("mail.smtp.socketFactory.fallback", "false");
            p.setProperty("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
            p.setProperty("mail.smtp.ssl.checkserveridentity", "false");
            p.setProperty("mail.smtp.ssl.trust", "*");
            if (conf.startTlsRequired) {
                p.setProperty("mail.smtp.starttls.required", "true");
            }
            conf.sslSocketFactory.ifPresentOrElse(sf -> {
                p.put("mail.smtp.ssl.socketFactory", sf);
            }, () -> {
                p.setProperty("mail.smtp.ssl.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            });
        }
        return p;
    }
}
