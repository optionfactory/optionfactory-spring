package net.optionfactory.spring.upstream.faults.spooler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailSenderAndCopyAddresses;
import net.optionfactory.spring.email.marshaller.EmailMarshaller;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.Result;
import net.optionfactory.spring.upstream.UpstreamFaultsSpooler;
import net.optionfactory.spring.upstream.UpstreamFaultsSpooler.UpstreamFault;
import org.springframework.util.Assert;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

public class FaultsEmailsSpooler<T> implements UpstreamFaultsSpooler<T> {

    private final EmailMarshaller emailMarshaller = new EmailMarshaller();
    private final EmailSenderAndCopyAddresses senderAndCopyAddresses;
    private final SubjectTemplateConfiguration emailSubjects;
    private final TemplateEngine emailTemplates;
    private final TemplateEngine stringTemplates;
    private final String emailTemplateName;
    private final Path emailSpoolDirectory;
    private final String recipient;
    private final ConcurrentLinkedQueue<UpstreamFault<T>> faults;

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

    public synchronized Result<Path> spool() {
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
            message.htmlBody = emailTemplates.process("email.faults.inlined.html", context);
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
