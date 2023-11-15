package net.optionfactory.spring.upstream.faults.spooler;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailPaths;
import net.optionfactory.spring.email.EmailSenderAndCopyAddresses;
import net.optionfactory.spring.email.marshaller.EmailMarshaller;
import net.optionfactory.spring.upstream.UpstreamFaultsSpooler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

public class FaultsEmailsSpooler<T> implements UpstreamFaultsSpooler<T> {

    private final Logger logger = LoggerFactory.getLogger(FaultsEmailsSpooler.class);

    private final EmailMarshaller emailMarshaller = new EmailMarshaller();
    private final EmailSenderAndCopyAddresses senderAndCopyAddresses;
    private final SubjectTemplateConfiguration emailSubjects;
    private final TemplateEngine emailTemplates;
    private final TemplateEngine stringTemplates;
    private final String emailTemplateName;
    private final EmailPaths paths;
    private final String recipient;
    private final ConcurrentLinkedQueue<UpstreamFault<T>> faults;

    private final AtomicReference<Instant> lastFaultSpool = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<Duration> gracePeriod = new AtomicReference<>(Duration.ofMinutes(5));

    public FaultsEmailsSpooler(EmailSenderAndCopyAddresses emailSenderConfiguration, SubjectTemplateConfiguration emailSubjects, TemplateEngine emailTemplates, TemplateEngine stringTemplates, String emailTemplateName, EmailPaths paths, String recipient) {
        this.senderAndCopyAddresses = emailSenderConfiguration;
        this.emailSubjects = emailSubjects;
        this.emailTemplates = emailTemplates;
        this.stringTemplates = stringTemplates;
        this.emailTemplateName = emailTemplateName;
        this.paths = paths;
        this.recipient = recipient;
        this.faults = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void add(UpstreamFault<T> fault) {
        faults.add(fault);
    }

    private final ReentrantLock spoolLock = new ReentrantLock();

    public void spool() {
        try {
            if (!spoolLock.tryLock(1, TimeUnit.SECONDS)) {
                return;
            }
        } catch (InterruptedException ex) {
            return;
        }
        try {
            var now = Instant.now();
            if (Duration.between(lastFaultSpool.get(), now).compareTo(gracePeriod.get()) < 0) {
                //max one email every grace period
                return;
            }
            final List<UpstreamFault<T>> batch = drain();
            if (batch.isEmpty()) {
                return;
            }
            final var message = new EmailMessage();
            final var context = new Context();
            context.setVariable("faults", batch);
            context.setVariable("tag", emailSubjects.subjectTag);
            message.subject = stringTemplates.process(emailSubjects.subjectTemplate.replace("{", "${"), context);
            message.htmlBody = emailTemplates.process(emailTemplateName, context);
            message.textBody = null;
            message.messageId = Long.toString(Instant.now().toEpochMilli());
            message.recipient = recipient;
            final Path p = emailMarshaller.marshalToSpool(senderAndCopyAddresses, message, List.of(), List.of(), paths, "faults.");
            faults.clear();
            lastFaultSpool.set(now);
            logger.info("[spool-emails][faults] spooled {}", p.getFileName());
        } catch (Exception ex) {
            logger.warn("[spool-emails][faults] failed to dump email", ex);
        } finally {
            spoolLock.unlock();
        }
    }

    private List<UpstreamFault<T>> drain() {
        final List<UpstreamFault<T>> batch = new ArrayList<>();
        UpstreamFault<T> fault;
        while ((fault = faults.poll()) != null) {
            batch.add(fault);
        }
        return batch;
    }

}
