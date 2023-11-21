package net.optionfactory.spring.upstream.faults.spooler;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailPaths;
import net.optionfactory.spring.email.spooling.BufferedScheduledSpooler;
import net.optionfactory.spring.email.spooling.Spooler;
import net.optionfactory.spring.upstream.faults.UpstreamFaultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.TaskScheduler;

public class FaultsEmailsSpooler implements Spooler<List<UpstreamFaultEvent>> {

    private final Logger logger = LoggerFactory.getLogger(FaultsEmailsSpooler.class);
    private final EmailPaths paths;
    private final EmailMessage.Prototype emailMessagePrototype;
    private final ApplicationEventPublisher publisher;

    public FaultsEmailsSpooler(EmailPaths paths, EmailMessage.Prototype emailMessagePrototype, ApplicationEventPublisher publisher) {
        this.paths = paths;
        this.emailMessagePrototype = emailMessagePrototype;
        this.publisher = publisher;
    }

    @Override
    public List<Path> spool(List<UpstreamFaultEvent> faults) {
        try {
            final var p = emailMessagePrototype.builder()
                    .variable("faults", faults)
                    .marshalToSpool(paths, "faults.", publisher);
            logger.info("[spool-emails][faults] spooled {} with {} faults", p.getFileName(), faults.size());
            return List.of(p);
        } catch (RuntimeException ex) {
            logger.warn("[spool-emails][faults] failed to dump email", ex);
            return List.of();
        }
    }

    public static BufferedScheduledSpooler<UpstreamFaultEvent> bufferedScheduled(EmailPaths paths, EmailMessage.Prototype emailMessagePrototype, ConfigurableApplicationContext ac, TaskScheduler ts, Duration initialDelay,
            Duration rate,
            Duration gracePeriod) {
        final var spooler = new FaultsEmailsSpooler(paths, emailMessagePrototype, ac);
        return new BufferedScheduledSpooler<>(
                UpstreamFaultEvent.class,
                ac,
                ts,
                initialDelay,
                rate,
                gracePeriod,
                spooler
        );
    }

}
