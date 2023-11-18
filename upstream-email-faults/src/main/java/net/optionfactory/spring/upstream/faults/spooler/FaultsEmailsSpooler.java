package net.optionfactory.spring.upstream.faults.spooler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailPaths;
import net.optionfactory.spring.email.marshaller.EmailMarshaller;
import net.optionfactory.spring.upstream.faults.UpstreamFaults.UpstreamFaultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FaultsEmailsSpooler {

    private final Logger logger = LoggerFactory.getLogger(FaultsEmailsSpooler.class);

    private final EmailPaths paths;
    private final EmailMessage.Prototype emailMessagePrototype;
    private final String subjectTag;

    private final ConcurrentLinkedQueue<UpstreamFaultEvent> faults = new ConcurrentLinkedQueue<>();
    private final EmailMarshaller emailMarshaller = new EmailMarshaller();

    public FaultsEmailsSpooler(EmailPaths paths, EmailMessage.Prototype emailMessagePrototype, String subjectTag) {
        this.paths = paths;
        this.emailMessagePrototype = emailMessagePrototype;
        this.subjectTag = subjectTag;
    }

    public void add(UpstreamFaultEvent event) {
        faults.add(event);
    }

    public int spool() {
        try {
            final List<UpstreamFaultEvent> batch = drain();
            if (batch.isEmpty()) {
                return 0;
            }
            final var message = emailMessagePrototype.builder()
                    .variable("tag", subjectTag)
                    .variable("faults", batch)
                    .build();
            final Path p = emailMarshaller.marshalToSpool(message, paths, "faults.");
            faults.clear();
            logger.info("[spool-emails][faults] spooled {}", p.getFileName());
            return batch.size();
        } catch (Exception ex) {
            logger.warn("[spool-emails][faults] failed to dump email", ex);
            return 0;
        }
    }

    private List<UpstreamFaultEvent> drain() {
        final List<UpstreamFaultEvent> batch = new ArrayList<>();
        UpstreamFaultEvent fault;
        while ((fault = faults.poll()) != null) {
            batch.add(fault);
        }
        return batch;
    }

}
