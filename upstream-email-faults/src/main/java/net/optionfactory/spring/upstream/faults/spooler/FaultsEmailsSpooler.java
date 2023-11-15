package net.optionfactory.spring.upstream.faults.spooler;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailPaths;
import net.optionfactory.spring.email.EmailSenderAndCopyAddresses;
import net.optionfactory.spring.email.marshaller.EmailMarshaller;
import net.optionfactory.spring.thymeleaf.SingletonDialect;
import net.optionfactory.spring.upstream.rendering.BodyRendering;
import net.optionfactory.spring.upstream.faults.UpstreamFaults.UpstreamFaultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

public class FaultsEmailsSpooler {

    private final Logger logger = LoggerFactory.getLogger(FaultsEmailsSpooler.class);

    private final EmailPaths paths;
    private final EmailSenderAndCopyAddresses senderAndCopyAddresses;
    private final SubjectTemplate subjectTemplate;
    private final String recipient;
    private final String emailTemplateName;

    private final TemplateEngine emailTemplates;
    private final TemplateEngine stringTemplates;
    private final ConcurrentLinkedQueue<UpstreamFaultEvent> faults;
    private final EmailMarshaller emailMarshaller = new EmailMarshaller();

    public record SubjectTemplate(String template, String tag) {

    }

    public FaultsEmailsSpooler(EmailPaths paths,
            EmailSenderAndCopyAddresses senderAndCopyAddresses,
            SubjectTemplate subjectTemplate,
            String recipient, String emailTemplateName, TemplateEngine emailTemplates, TemplateEngine stringTemplates) {
        this.paths = paths;
        this.senderAndCopyAddresses = senderAndCopyAddresses;
        this.subjectTemplate = subjectTemplate;
        this.recipient = recipient;
        this.emailTemplateName = emailTemplateName;
        this.emailTemplates = emailTemplates;
        this.stringTemplates = stringTemplates;
        this.faults = new ConcurrentLinkedQueue<>();

        this.emailTemplates.addDialect(new SingletonDialect("bodies", new Object() {
            public String abbreviated(byte[] in, int maxSize) {
                return BodyRendering.abbreviated(in, "✂️", maxSize);
            }
        }));
    }

    public static FaultsEmailsSpooler withDefaultTemplateEngines(
            EmailPaths paths, EmailSenderAndCopyAddresses emailSenderConfiguration,
            SubjectTemplate subjectTemplate,
            String recipient,
            String emailTemplatePrefix,
            String emailTemplateName) {

        final var htmlResolver = new ClassLoaderTemplateResolver();
        htmlResolver.setOrder(1);
        htmlResolver.setResolvablePatterns(Collections.singleton("*.html"));
        htmlResolver.setPrefix(emailTemplatePrefix);
        htmlResolver.setTemplateMode(TemplateMode.HTML);
        htmlResolver.setCharacterEncoding("utf-8");
        htmlResolver.setCacheable(true);

        final var stringResolver = new StringTemplateResolver();
        stringResolver.setOrder(1);
        stringResolver.setTemplateMode(TemplateMode.TEXT);
        stringResolver.setCacheable(true);

        final var emailTemplateEngine = new SpringTemplateEngine();
        emailTemplateEngine.addTemplateResolver(htmlResolver);

        final var stringTemplateEngine = new SpringTemplateEngine();
        stringTemplateEngine.addTemplateResolver(stringResolver);

        return new FaultsEmailsSpooler(
                paths, emailSenderConfiguration,
                subjectTemplate, recipient, emailTemplateName,
                emailTemplateEngine,
                stringTemplateEngine);
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
            final var message = new EmailMessage();
            final var context = new Context();
            context.setVariable("faults", batch);
            context.setVariable("tag", subjectTemplate.tag());
            message.subject = stringTemplates.process(subjectTemplate.template().replace("{", "${"), context);
            message.htmlBody = emailTemplates.process(emailTemplateName, context);
            message.textBody = null;
            message.messageId = Long.toString(Instant.now().toEpochMilli());
            message.recipient = recipient;
            final Path p = emailMarshaller.marshalToSpool(senderAndCopyAddresses, message, List.of(), List.of(), paths, "faults.");
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
