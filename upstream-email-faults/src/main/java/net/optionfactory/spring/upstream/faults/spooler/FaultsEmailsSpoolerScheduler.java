package net.optionfactory.spring.upstream.faults.spooler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import net.optionfactory.spring.email.scheduling.EmailSenderScheduler.EmailSpooled;
import net.optionfactory.spring.upstream.faults.UpstreamFaults.UpstreamFaultEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

public class FaultsEmailsSpoolerScheduler {

    private final FaultsEmailsSpooler spooler;
    private final ApplicationEventPublisher publisher;
    private final ReentrantLock spoolLock = new ReentrantLock();
    private final AtomicReference<Instant> latestSpool = new AtomicReference<>(Instant.EPOCH);

    public FaultsEmailsSpoolerScheduler(FaultsEmailsSpooler spooler, ApplicationEventPublisher publisher) {
        this.spooler = spooler;
        this.publisher = publisher;
    }

    public record UpstreamFaultReceived() {
    }

    @EventListener
    public UpstreamFaultReceived onFault(UpstreamFaultEvent fault) {
        spooler.add(fault);
        return new UpstreamFaultReceived();
    }

    @EventListener
    @Async
    public void onFaultReceived(UpstreamFaultReceived r) {
        trySpool();
    }

    @Scheduled(
            initialDelayString = "${faults.email.spooler.initialdelay.seconds:60}",
            fixedRateString = "${faults.email.spooler.rate.seconds:300}",
            timeUnit = TimeUnit.SECONDS
    )
    public void trySpool() {
        try {
            if (!spoolLock.tryLock(1, TimeUnit.SECONDS)) {
                return;
            }
        } catch (InterruptedException ex) {
            return;
        }
        try {
            var now = Instant.now();
            if (Duration.between(latestSpool.get(), now).compareTo(Duration.ofMinutes(5)) < 0) {
                //max one email every grace period
                return;
            }
            if (spooler.spool() != 0) {
                latestSpool.set(now);
                publisher.publishEvent(new EmailSpooled());
            }
        } finally {
            spoolLock.unlock();
        }
    }

}
