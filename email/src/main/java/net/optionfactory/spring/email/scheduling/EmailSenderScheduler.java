package net.optionfactory.spring.email.scheduling;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import net.optionfactory.spring.email.connector.EmailSender;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

public class EmailSenderScheduler {

    private final EmailSender sender;
    private final ReentrantLock lock = new ReentrantLock();

    public EmailSenderScheduler(EmailSender sender) {
        this.sender = sender;
    }

    public record EmailSpooled() {
    }

    @Async
    @EventListener
    public void onEmailSpooled(EmailSpooled req) {
        tryProcessSpool();
    }

    @Scheduled(
            initialDelayString = "${email.scheduler.initialdelay.seconds:10}",
            fixedRateString = "${email.scheduler.rate.seconds:60}",
            timeUnit = TimeUnit.SECONDS
    )
    public void tryProcessSpool() {
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
}
