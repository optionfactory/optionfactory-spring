package net.optionfactory.spring.email;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

/// A `TaskScheduler` that runs nothing by itself: tests run the scheduled tasks explicitly, on the
/// test thread, so that scheduling behaviour is verified without waiting on a clock.
public class ManualTaskScheduler implements TaskScheduler {

    public record Periodic(Runnable task, Instant startTime, Duration period) {

    }

    public final List<Periodic> periodic = new ArrayList<>();
    public final List<Runnable> oneShots = new ArrayList<>();

    /// Runs the one-shot tasks scheduled so far, and forgets them.
    public void runOneShots() {
        final var tasks = new ArrayList<>(oneShots);
        oneShots.clear();
        tasks.forEach(Runnable::run);
    }

    /// Runs one execution of every periodic task.
    public void tickPeriodic() {
        periodic.forEach(p -> p.task().run());
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        throw new UnsupportedOperationException("not used");
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        oneShots.add(task);
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
        periodic.add(new Periodic(task, startTime, period));
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        throw new UnsupportedOperationException("not used");
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        throw new UnsupportedOperationException("not used");
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        throw new UnsupportedOperationException("not used");
    }

}
