package net.optionfactory.spring.email.spooling;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.optionfactory.spring.email.ManualTaskScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

public class BufferedScheduledSpoolerTest {

    public record TestEvent(int id) {

    }

    public record OtherEvent(int id) {

    }

    private final GenericApplicationContext ac = new GenericApplicationContext();
    private final ManualTaskScheduler ts = new ManualTaskScheduler();
    private final List<List<TestEvent>> batches = new ArrayList<>();

    @AfterEach
    public void close() {
        ac.close();
    }

    private BufferedScheduledSpooler<TestEvent> spooler(Duration gracePeriod, Spooler<List<TestEvent>> spooler) {
        final var s = new BufferedScheduledSpooler<>(TestEvent.class, ac, ts, Duration.ofSeconds(3), Duration.ofMinutes(1), gracePeriod, spooler);
        ac.refresh();
        return s;
    }

    private BufferedScheduledSpooler<TestEvent> recordingSpooler(Duration gracePeriod) {
        return spooler(gracePeriod, batch -> {
            batches.add(batch);
            return List.of(Path.of("spooled"));
        });
    }

    @Test
    public void constructionSchedulesTheDrainAtTheConfiguredRate() {
        recordingSpooler(Duration.ZERO);

        Assertions.assertEquals(1, ts.periodic.size(), "one periodic drain is scheduled");
        Assertions.assertEquals(Duration.ofMinutes(1), ts.periodic.get(0).period(), "the periodic drain runs at the configured rate");
    }

    @Test
    public void eventsPublishedTogetherAreSpooledAsOneBatchInPublicationOrder() {
        recordingSpooler(Duration.ZERO);

        ac.publishEvent(new TestEvent(1));
        ac.publishEvent(new TestEvent(2));
        ts.runOneShots();

        Assertions.assertEquals(1, batches.size(), "the first drain takes the whole buffer, the second finds it empty");
        Assertions.assertEquals(List.of(new TestEvent(1), new TestEvent(2)), batches.get(0), "a batch keeps the publication order");
    }

    @Test
    public void eachEventSchedulesAnImmediateDrain() {
        recordingSpooler(Duration.ZERO);

        ac.publishEvent(new TestEvent(1));
        ac.publishEvent(new TestEvent(2));

        Assertions.assertEquals(2, ts.oneShots.size(), "every buffered event schedules a drain right away");
    }

    @Test
    public void eventsOfOtherTypesAreNotBuffered() {
        recordingSpooler(Duration.ZERO);

        ac.publishEvent(new OtherEvent(1));
        ts.runOneShots();
        ts.tickPeriodic();

        Assertions.assertTrue(ts.oneShots.isEmpty(), "events of another type schedule no drain");
        Assertions.assertTrue(batches.isEmpty(), "events of another type are never spooled");
    }

    @Test
    public void anEmptyBufferIsNotSpooled() {
        recordingSpooler(Duration.ZERO);

        ts.tickPeriodic();

        Assertions.assertTrue(batches.isEmpty(), "the spooler is not invoked without buffered events");
    }

    @Test
    public void eventsWithinTheGracePeriodWaitInTheBuffer() {
        recordingSpooler(Duration.ofHours(1));

        ac.publishEvent(new TestEvent(1));
        ts.runOneShots();
        ac.publishEvent(new TestEvent(2));
        ts.runOneShots();
        ts.tickPeriodic();

        Assertions.assertEquals(List.of(List.of(new TestEvent(1))), batches, "at most one batch is spooled per grace period, later events wait");
    }

    @Test
    public void withoutGracePeriodEveryDrainSpoolsWhatIsBuffered() {
        recordingSpooler(Duration.ZERO);

        ac.publishEvent(new TestEvent(1));
        ts.runOneShots();
        ac.publishEvent(new TestEvent(2));
        ts.runOneShots();

        Assertions.assertEquals(List.of(List.of(new TestEvent(1)), List.of(new TestEvent(2))), batches, "each drain spools the events buffered since the previous one");
    }

    @Test
    public void aFailingBatchIsDroppedAndDoesNotStartTheGracePeriod() {
        final var attempts = new ArrayList<List<TestEvent>>();
        spooler(Duration.ofHours(1), batch -> {
            attempts.add(batch);
            if (batch.contains(new TestEvent(1))) {
                throw new IllegalStateException("simulated spool failure");
            }
            return List.of();
        });

        ac.publishEvent(new TestEvent(1));
        Assertions.assertThrows(IllegalStateException.class, ts::runOneShots, "the spooler failure surfaces to the scheduler");
        ac.publishEvent(new TestEvent(2));
        ts.runOneShots();

        Assertions.assertEquals(List.of(List.of(new TestEvent(1)), List.of(new TestEvent(2))), attempts, "the failed batch is not retried, and the next one is spooled despite the grace period");
    }
}
