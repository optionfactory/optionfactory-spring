package net.optionfactory.spring.upstream.faults.spooler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import net.optionfactory.spring.email.EmailPaths;
import net.optionfactory.spring.email.EmailSenderAndCopyAddresses;
import net.optionfactory.spring.email.connector.EmailConnector;
import net.optionfactory.spring.email.connector.EmailSender;
import net.optionfactory.spring.email.scheduling.EmailSenderScheduler;
import net.optionfactory.spring.upstream.faults.UpstreamFaults.UpstreamFaultEvent;
import net.optionfactory.spring.upstream.faults.spooler.FaultsEmailsSpoolerTest.Conf;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = Conf.class)
public class FaultsEmailsSpoolerTest {

    @EnableScheduling
    @EnableAsync
    public static class Conf {

        @Bean
        public TaskScheduler taskScheduler() {
            final var ts = new SimpleAsyncTaskScheduler();
            ts.setVirtualThreads(true);
            ts.setThreadNamePrefix("tasks-vt-");
            return ts;
        }

        @Bean
        public EmailPaths paths() throws IOException {
            return EmailPaths.provide(Path.of("target", "spool"), Path.of("target", "sent"), null);
        }

        @Bean
        public EmailSenderScheduler sender(EmailPaths paths) {
            final EmailConnector connector = null;
            final Duration deadAfter = null;
            return new EmailSenderScheduler(new EmailSender(true, paths, connector, deadAfter));
        }

        @Bean
        public FaultsEmailsSpoolerScheduler faultsSpooler(EmailPaths paths, ApplicationEventPublisher publisher) throws IOException {
            final var sender = new EmailSenderAndCopyAddresses();
            sender.sender = "test@example.com";
            sender.senderDescription = null;
            sender.ccAddresses = List.of();
            sender.bccAddresses = List.of();

            final var subject = new FaultsEmailsSpooler.SubjectTemplate("subject", "");
            final var templateName = "example-email.faults.inlined.html";
            final var recipient = "recipient@example.com";

            final var spooler = FaultsEmailsSpooler.withDefaultTemplateEngines(
                    paths,
                    sender,
                    subject,
                    recipient,
                    "/email/",
                    templateName
            );

            return new FaultsEmailsSpoolerScheduler(spooler, publisher);
        }

    }

    @Autowired
    public ApplicationEventPublisher publisher;
    @Autowired
    public EmailPaths paths;

    @Test
    public void exampleUsage() throws IOException, ReflectiveOperationException, InterruptedException {
        emlsIn(paths.spool).forEach(this::delete);
        emlsIn(paths.sent).forEach(this::delete);

        final Object principal = null;

        publisher.publishEvent(new UpstreamFaultEvent(
                "boot-id",
                "upstream-id",
                principal,
                "endpoint",
                Object.class.getMethod("toString"),
                new Object[0],
                100,
                Instant.now(),
                URI.create("https://example.com"),
                HttpMethod.PATCH,
                new HttpHeaders(),
                "request_body".repeat(1000).getBytes(StandardCharsets.UTF_8),
                Instant.now(),
                new HttpHeaders(),
                HttpStatusCode.valueOf(400),
                "response_body".repeat(1000).getBytes(StandardCharsets.UTF_8),
                null));

        Thread.sleep(Duration.ofMillis(2000));
        Assert.assertEquals(1, emlsIn(paths.sent).count());
    }

    private Stream<Path> emlsIn(Path path) throws IOException {
        return Files.list(path)
                .filter(p -> p.getFileName().toString().endsWith(".eml"));
    }

    private void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
