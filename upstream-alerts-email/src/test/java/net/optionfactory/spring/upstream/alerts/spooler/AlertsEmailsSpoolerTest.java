package net.optionfactory.spring.upstream.alerts.spooler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailPaths;
import net.optionfactory.spring.email.EmailSender;
import net.optionfactory.spring.email.EmailSenderConfiguration;
import net.optionfactory.spring.email.ScheduledEmailSender;
import net.optionfactory.spring.email.spooling.BufferedScheduledSpooler;
import net.optionfactory.spring.thymeleaf.SingletonDialect;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.alerts.UpstreamAlertEvent;
import net.optionfactory.spring.upstream.alerts.spooler.AlertsEmailsSpoolerTest.Conf;
import net.optionfactory.spring.upstream.buffering.Buffering;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = Conf.class)
public class AlertsEmailsSpoolerTest {

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
        public ScheduledEmailSender sender(EmailPaths paths, ConfigurableApplicationContext ac, TaskScheduler ts) {
            final var conf = EmailSenderConfiguration.builder()
                    .placebo(true)
                    .host("example.com")
                    .port(25)
                    .protocol(EmailSenderConfiguration.Protocol.PLAIN)
                    .build();
            return new ScheduledEmailSender(new EmailSender(paths, conf), ac, ts, Duration.ofSeconds(0), Duration.ofSeconds(5));
        }

        @Bean
        public BufferedScheduledSpooler<UpstreamAlertEvent> alertsSpooler(EmailPaths paths, ConfigurableApplicationContext ac, TaskScheduler ts) throws IOException {
            final var messagePrototype = EmailMessage.builder()
                    .sender("test@example.com", null)
                    .recipient("recipient@example.com")
                    .subject("Subject")
                    .htmlBodyEngine("/email/", new SingletonDialect("bodies", new AlertBodiesFunctions()))
                    .htmlBodyTemplate("example-email.alerts.inlined.html")
                    .prototype();

            return AlertsEmailsSpooler.bufferedScheduled(
                    paths,
                    messagePrototype,
                    ac,
                    ts,
                    Duration.ofSeconds(0),
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(10)
            );

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

        final var event = new UpstreamAlertEvent(
                new InvocationContext(
                        new Expressions(null, null),
                        new InvocationContext.HttpMessageConverters(List.of()),
                        new EndpointDescriptor("upstream", "endpoint", Object.class.getMethod("toString"), null),
                        new Object[0],
                        "boot-id",
                        1,
                        principal,
                        Buffering.BUFFERED
                ),
                new RequestContext(
                        Instant.now(),
                        HttpMethod.PATCH,
                        URI.create("https://example.com"),
                        new HttpHeaders(),
                        new HashMap<>(),
                        "request_body".repeat(1000).getBytes(StandardCharsets.UTF_8)
                ),
                new ResponseContext(
                        Instant.now(),
                        HttpStatus.OK,
                        HttpStatus.OK.getReasonPhrase(),
                        new HttpHeaders(),
                        BodySource.of("response_body".repeat(1000), StandardCharsets.UTF_8),
                        false
                ),
                null);

        publisher.publishEvent(event);

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
