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

public class ScheduledEmailSender {

    private final EmailSender sender;
    private final ReentrantLock lock = new ReentrantLock();

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
