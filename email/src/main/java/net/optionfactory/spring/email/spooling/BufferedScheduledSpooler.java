package net.optionfactory.spring.email.spooling;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.scheduling.TaskScheduler;

/// Collects application events of a given type and hands them to a [Spooler] in batches, so that a
/// burst of events becomes a few spooled artifacts (e.g. one alert email) instead of one each.
///
/// Every event published as a payload of exactly type `T` (i.e. `publisher.publishEvent(payload)`,
/// which wraps it in a `PayloadApplicationEvent`) is buffered, and a drain is scheduled right away. A drain
/// takes the whole buffer and spools it as one batch, but at most once per `gracePeriod`: a drain
/// coming too soon after the previous successful one leaves the buffer untouched, and the events
/// wait for a later drain. The drains scheduled at a fixed `rate` are what eventually picks them
/// up, so events can wait up to about `rate` after the grace period ends.
///
/// Drains never overlap: one that cannot acquire the lock within a second is skipped. The buffer
/// is cleared before the spooler is invoked, so a failing batch is lost: see [Spooler] for the
/// consequences. The grace period only starts after a successful spool.
///
/// Nothing is drained when the application shuts down, so events still buffered at that time are
/// lost.
///
/// @param <T> the type of the event payloads
public class BufferedScheduledSpooler<T> {

    private final ReentrantLock spoolLock = new ReentrantLock();
    private final ReentrantLock bufferLock = new ReentrantLock();
    private final AtomicReference<Instant> latestSpool = new AtomicReference<>(Instant.EPOCH);
    private final List<T> buffer = new ArrayList<>();
    private final Duration gracePeriod;
    private final Spooler<List<T>> spooler;

    /// Registers the event listener and starts the schedule: there is nothing else to start.
    ///
    /// @param eventType the payload type to buffer; the match is exact, payloads of a subtype are
    /// not buffered
    /// @param applicationContext where events are listened for
    /// @param ts runs the periodic and the event-triggered drains
    /// @param initialDelay the delay of the first periodic drain
    /// @param rate the period between two periodic drains
    /// @param gracePeriod the minimum interval between two spooled batches
    /// @param spooler what each batch is handed to, on a scheduler thread
    public BufferedScheduledSpooler(
            Class<T> eventType,
            ConfigurableApplicationContext applicationContext,
            TaskScheduler ts,
            Duration initialDelay,
            Duration rate,
            Duration gracePeriod,
            Spooler<List<T>> spooler) {
        this.gracePeriod = gracePeriod;
        this.spooler = spooler;
        applicationContext.addApplicationListener(new AddToBufferAndScheduleNowListener(eventType, ts));
        ts.scheduleAtFixedRate(this::trySpool, Instant.now().plus(initialDelay), rate);
    }

    private void trySpool() {
        try {
            if (!spoolLock.tryLock(1, TimeUnit.SECONDS)) {
                return;
            }
        } catch (InterruptedException ex) {
            return;
        }
        try {
            var now = Instant.now();
            if (Duration.between(latestSpool.get(), now).compareTo(gracePeriod) < 0) {
                return;
            }
            final List<T> batch = drain();
            if (batch.isEmpty()) {
                return;
            }
            spooler.spool(batch);
            latestSpool.set(now);
        } finally {
            spoolLock.unlock();
        }
    }

    private List<T> drain() {
        bufferLock.lock();
        try {
            final var copy = new ArrayList<>(buffer);
            buffer.clear();
            return copy;
        } finally {
            bufferLock.unlock();
        }
    }

    private class AddToBufferAndScheduleNowListener implements GenericApplicationListener {

        private final ResolvableType applicationEventType;
        private final TaskScheduler ts;

        public AddToBufferAndScheduleNowListener(Class<?> eventType, TaskScheduler ts) {
            this.applicationEventType = ResolvableType.forClassWithGenerics(PayloadApplicationEvent.class, eventType);
            this.ts = ts;
        }

        @Override
        public boolean supportsEventType(ResolvableType eventType) {
            return applicationEventType.isAssignableFrom(eventType);
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            @SuppressWarnings("unchecked")
            final var payload = (T) ((PayloadApplicationEvent) event).getPayload();
            bufferLock.lock();
            try {
                buffer.add(payload);
            } finally {
                bufferLock.unlock();
            }
            ts.schedule(BufferedScheduledSpooler.this::trySpool, Instant.now());
        }
    }
}
