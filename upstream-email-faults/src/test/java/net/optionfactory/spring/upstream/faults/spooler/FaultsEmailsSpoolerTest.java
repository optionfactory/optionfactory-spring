package net.optionfactory.spring.upstream.faults.spooler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailPaths;
import net.optionfactory.spring.email.EmailSender;
import net.optionfactory.spring.email.EmailSenderConfiguration;
import net.optionfactory.spring.email.EmailSenderScheduler;
import net.optionfactory.spring.thymeleaf.SingletonDialect;
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
            final var conf = EmailSenderConfiguration.builder()
                    .placebo(true)
                    .host("example.com")
                    .port(25)
                    .protocol(EmailSenderConfiguration.Protocol.PLAIN)
                    .build();
            return new EmailSenderScheduler(new EmailSender(paths, conf));
        }

        @Bean
        public FaultsEmailsSpoolerScheduler faultsSpooler(EmailPaths paths, ApplicationEventPublisher publisher) throws IOException {
            final var messagePrototype = EmailMessage.builder()
                    .sender("test@example.com", null)
                    .recipient("recipient@example.com")
                    .subjectTemplateEngine()
                    .subjectTemplate("[{{tag}}] Subject")
                    .htmlBodyTemplateEngine("/email/", new SingletonDialect("bodies", new FaultBodiesFunctions()))
                    .htmlBodyTemplate("example-email.faults.inlined.html")
                    .prototype();

            final var spooler = new FaultsEmailsSpooler(paths, messagePrototype, "TESTING");
            return new FaultsEmailsSpoolerScheduler(spooler, publisher);
        }

    }

    @Autowired
    public ApplicationEventPublisher publisher;
    @Autowired
    public EmailPaths paths;

    @Test
    public void exampleUsage() throws IOException, ReflectiveOperationException, InterruptedException {
        emlsIn(paths.spool()).forEach(this::delete);
        emlsIn(paths.sent()).forEach(this::delete);

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

        Thread.sleep(Duration.ofMillis(500));
        Assert.assertEquals(1, emlsIn(paths.sent()).count());
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
