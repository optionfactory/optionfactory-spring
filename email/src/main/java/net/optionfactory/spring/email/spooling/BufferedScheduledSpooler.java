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

public class BufferedScheduledSpooler<T> {

    private final ReentrantLock spoolLock = new ReentrantLock();
    private final ReentrantLock bufferLock = new ReentrantLock();
    private final AtomicReference<Instant> latestSpool = new AtomicReference<>(Instant.EPOCH);
    private final List<T> buffer = new ArrayList<>();
    private final Duration gracePeriod;
    private final Spooler<List<T>> spooler;

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
                //max one email every grace period
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
