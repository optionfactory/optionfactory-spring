package net.optionfactory.spring.upstream.faults.spooler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailSenderAndCopyAddresses;
import net.optionfactory.spring.email.marshaller.EmailMarshaller;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.Result;
import net.optionfactory.spring.upstream.UpstreamFaultsSpooler;
import net.optionfactory.spring.upstream.UpstreamFaultsSpooler.UpstreamFault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
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
    private final Path emailSpoolDirectory;
    private final String recipient;
    private final ConcurrentLinkedQueue<UpstreamFault<T>> faults;

    private final AtomicReference<Instant> lastFaultSpool = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<Duration> gracePeriod = new AtomicReference<>(Duration.ofMinutes(5));    

    public FaultsEmailsSpooler(EmailSenderAndCopyAddresses emailSenderConfiguration, SubjectTemplateConfiguration emailSubjects, TemplateEngine emailTemplates, TemplateEngine stringTemplates, String emailTemplateName, Path emailSpoolDirectory, String recipient) {
        Assert.isTrue(Files.isDirectory(emailSpoolDirectory), "emailSpoolDirectory must be a directory");
        Assert.isTrue(Files.isWritable(emailSpoolDirectory), "emailSpoolDirectory must be writable");
        this.senderAndCopyAddresses = emailSenderConfiguration;
        this.emailSubjects = emailSubjects;
        this.emailTemplates = emailTemplates;
        this.stringTemplates = stringTemplates;
        this.emailTemplateName = emailTemplateName;
        this.emailSpoolDirectory = emailSpoolDirectory;
        this.recipient = recipient;
        this.faults = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void add(UpstreamFault<T> fault) {
        faults.add(fault);
    }

    public synchronized void spool() {
        var now = Instant.now();
        if (Duration.between(lastFaultSpool.get(), now).compareTo(gracePeriod.get()) < 0) {
            //max one email every grace period
            return;
        }
        final Result<Path> spooled = dumpToEml();
        if (spooled.isError()) {
            logger.warn("[spool-emails][faults] failed to dump email: {}", spooled.getErrors());
            return;
        }
        if (spooled.getValue() == null) {
            //success, but no cigar
            return;
        }
        logger.info("[spool-emails][faults] spooled {}", spooled.getValue().getFileName());
        lastFaultSpool.set(now);
    }

    private Result<Path> dumpToEml() {
        try {
            final List<UpstreamFault<T>> batch = drain();
            if(batch.isEmpty()){
                return Result.value(null);
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

            final Path tempPath = Files.createTempFile(emailSpoolDirectory, "faults.", ".tmp");
            emailMarshaller.marshal(senderAndCopyAddresses, message, List.of(), List.of(), tempPath);
            final Path targetPath = emailSpoolDirectory.resolve(tempPath.getFileName().toString().replace(".tmp", ".eml"));
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
            faults.clear();
            return Result.value(targetPath);
        } catch (IOException ex) {
            return Result.error(Problem.of("SPOOLING_ERROR", null, null, ex.getMessage()));
        }
    }

    private List<UpstreamFault<T>> drain() {
        final List<UpstreamFault<T>> batch = new ArrayList<>();
        UpstreamFault<T> fault;
        while((fault = faults.poll()) != null){
            batch.add(fault);
        }
        return batch;
    }

}
