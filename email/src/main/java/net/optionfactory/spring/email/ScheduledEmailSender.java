package net.optionfactory.spring.email;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.scheduling.TaskScheduler;

/// Runs an [EmailSender] periodically, and right away whenever an [EmailSpooled] event is published,
/// so that emails spooled with a publisher go out without waiting for the next period.
///
/// Passes never overlap: a pass that cannot acquire the lock within one second, because another one
/// is running, is skipped, which is safe since the running pass or the next one picks up whatever is
/// in the spool. That lock only covers this instance, so a spool must be processed by a single
/// `ScheduledEmailSender` (see [EmailSender#processSpool()]).
///
/// An exception escaping a periodic pass (the spool directory cannot be listed) reaches the task
/// scheduler, whose handling decides whether the periodic task survives it.
public class ScheduledEmailSender {

    private final EmailSender sender;
    private final ReentrantLock lock = new ReentrantLock();

    /// Starts the schedule and registers the event listener: there is nothing else to start.
    ///
    /// @param sender the sender to run
    /// @param applicationContext where [EmailSpooled] events are listened for
    /// @param ts runs both the periodic and the event-triggered passes
    /// @param initialDelay the delay of the first periodic pass
    /// @param rate the period between two periodic passes
    public ScheduledEmailSender(EmailSender sender, ConfigurableApplicationContext applicationContext, TaskScheduler ts, Duration initialDelay, Duration rate) {
        this.sender = sender;
        applicationContext.addApplicationListener(new ScheduleNowEventListener(EmailSpooled.class, ts, this::tryProcessSpool));
        ts.scheduleAtFixedRate(this::tryProcessSpool, Instant.now().plus(initialDelay), rate);
    }

    private void tryProcessSpool() {
        try {
            if (!lock.tryLock(1, TimeUnit.SECONDS)) {
                return;
            }
        } catch (InterruptedException ex) {
            return;
        }
        try {
            sender.processSpool();
        } finally {
            lock.unlock();
        }
    }

    private static class ScheduleNowEventListener implements GenericApplicationListener {

        private final ResolvableType applicationEventType;
        private final TaskScheduler ts;
        private final Runnable runnable;

        public ScheduleNowEventListener(Class<?> eventType, TaskScheduler ts, Runnable runnable) {
            this.applicationEventType = ResolvableType.forClassWithGenerics(PayloadApplicationEvent.class, eventType);
            this.ts = ts;
            this.runnable = runnable;
        }

        @Override
        public boolean supportsEventType(ResolvableType eventType) {
            return applicationEventType.isAssignableFrom(eventType);
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            ts.schedule(runnable, Instant.now());
        }
    }
}
