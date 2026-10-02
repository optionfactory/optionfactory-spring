package net.optionfactory.spring.email;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.GenericApplicationContext;

public class ScheduledEmailSenderTest {

    @TempDir
    Path tmp;

    private final GenericApplicationContext ac = new GenericApplicationContext();
    private final ManualTaskScheduler ts = new ManualTaskScheduler();

    @AfterEach
    public void close() {
        ac.close();
    }

    private EmailPaths scheduledPlaceboSender() {
        final var paths = EmailPaths.provide(tmp.resolve("spool"), tmp.resolve("sent"), null);
        final var conf = EmailSenderConfiguration.builder()
                .placebo(true)
                .host("example.com")
                .port(25)
                .protocol(EmailSenderConfiguration.Protocol.PLAIN)
                .build();
        new ScheduledEmailSender(new EmailSender(paths, conf), ac, ts, Duration.ofSeconds(10), Duration.ofMinutes(1));
        ac.refresh();
        return paths;
    }

    @Test
    public void thePeriodicPassProcessesTheSpool() throws IOException {
        final var paths = scheduledPlaceboSender();
        Files.createFile(paths.spool().resolve("a.eml"));

        Assertions.assertEquals(1, ts.periodic.size(), "one periodic pass is scheduled");
        Assertions.assertEquals(Duration.ofMinutes(1), ts.periodic.get(0).period(), "the periodic pass runs at the configured rate");
        ts.tickPeriodic();

        Assertions.assertTrue(Files.exists(paths.sent().resolve("a.eml")), "the periodic pass sends what is in the spool");
    }

    @Test
    public void anEmailSpooledEventTriggersAnImmediatePass() throws IOException {
        final var paths = scheduledPlaceboSender();
        Files.createFile(paths.spool().resolve("a.eml"));

        ac.publishEvent(new EmailSpooled());
        Assertions.assertEquals(1, ts.oneShots.size(), "an EmailSpooled event schedules a pass right away");
        ts.runOneShots();

        Assertions.assertTrue(Files.exists(paths.sent().resolve("a.eml")), "the event-triggered pass sends what is in the spool");
    }

    @Test
    public void otherEventsDoNotTriggerAPass() {
        scheduledPlaceboSender();

        ac.publishEvent("an unrelated payload");

        Assertions.assertTrue(ts.oneShots.isEmpty(), "only EmailSpooled events schedule an immediate pass");
    }
}
