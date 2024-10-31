package net.optionfactory.spring.upstream.alerts.spooler;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailPaths;
import net.optionfactory.spring.email.spooling.BufferedScheduledSpooler;
import net.optionfactory.spring.email.spooling.Spooler;
import net.optionfactory.spring.upstream.alerts.UpstreamAlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.TaskScheduler;

public class AlertsEmailsSpooler implements Spooler<List<UpstreamAlertEvent>> {

    private final Logger logger = LoggerFactory.getLogger(AlertsEmailsSpooler.class);
    private final EmailMessage.Prototype emailMessagePrototype;

    public AlertsEmailsSpooler(EmailMessage.Prototype emailMessagePrototype) {
        this.emailMessagePrototype = emailMessagePrototype;
    }

    @Override
    public List<Path> spool(List<UpstreamAlertEvent> alerts) {
        try {
            final var p = emailMessagePrototype.builder()
                    .variable("alerts", alerts)
                    .marshalToSpool();
            logger.info("[spool-emails][alerts] spooled {} with {} alerts", p.getFileName(), alerts.size());
            return List.of(p);
        } catch (RuntimeException ex) {
            logger.warn("[spool-emails][alerts] failed to dump email", ex);
            return List.of();
        }
    }

    public static BufferedScheduledSpooler<UpstreamAlertEvent> bufferedScheduled(EmailPaths paths, EmailMessage.Prototype emailMessagePrototype, ConfigurableApplicationContext ac, TaskScheduler ts, Duration initialDelay,
            Duration rate,
            Duration gracePeriod) {
        final var emp  = emailMessagePrototype.builder().spooling(paths, "alerts.", ac).prototype();
        final var spooler = new AlertsEmailsSpooler(emp);
        return new BufferedScheduledSpooler<>(
                UpstreamAlertEvent.class,
                ac,
                ts,
                initialDelay,
                rate,
                gracePeriod,
                spooler
        );
    }

}
