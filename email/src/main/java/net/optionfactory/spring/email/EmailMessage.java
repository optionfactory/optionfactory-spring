package net.optionfactory.spring.email;

import jakarta.mail.internet.InternetAddress;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.optionfactory.spring.email.EmailMarshaller.EmailMarshallingException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

public record EmailMessage(
        @NonNull
        String messageId,
        @NonNull
        InternetAddress sender,
        @NonNull
        InternetAddress[] replyTo,
        @NonNull
        InternetAddress[] recipients,
        @NonNull
        InternetAddress[] ccAddresses,
        @NonNull
        InternetAddress[] bccAddresses,
        @NonNull
        String subject,
        @Nullable
        String textBody,
        @Nullable
        String htmlBody,
        @NonNull
        List<AttachmentSource> attachments,
        @NonNull
        List<CidSource> cids,
        @Nullable Spooling spoolConfig) {

    public record Spooling(
            @NonNull
            EmailPaths paths,
            @Nullable
            String prefix,
            @Nullable
            ApplicationEventPublisher publisher) {

    }

    public static Builder builder() {
        return new Builder();
    }

    public interface Prototype {

        Builder builder();
    }

    public static class Builder implements Prototype {

        private String messageId;
        private InternetAddress sender;
        private InternetAddress[] replyTo;
        private InternetAddress[] recipients;
        private InternetAddress[] ccAddresses;
        private InternetAddress[] bccAddresses;

        private String subject;
        private ITemplateEngine textBodyEngine;
        private String textBodyTemplate;
        private String textBodyLiteral;
        private ITemplateEngine htmlBodyEngine;
        private String htmlBodyTemplate;
        private String htmlBodyLiteral;
        private HtmlBodyPostprocessor htmlBodyPostprocessor;

        private List<AttachmentSource> attachments = new ArrayList<>();
        private List<CidSource> cids = new ArrayList<>();
        private ConfigurableApplicationContext applicationContext;

        private Map<String, Object> variables = new HashMap<>();

        private Spooling spooling;

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        private static InternetAddress makeAddress(String address, String personal) {
            try {
                return new InternetAddress(address, personal, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                throw new EmailMarshallingException("unparseable address", ex);
            }
        }

        private static InternetAddress[] makeAddresses(List<String> addresses) {
            return addresses.stream()
                    .map(a -> makeAddress(a, null))
                    .toArray(i -> new InternetAddress[i]);
        }

        public Builder sender(String sender, @Nullable String description) {
            this.sender = makeAddress(sender, description);
            return this;
        }

        public Builder replyTo(String to) {
            this.replyTo = makeAddresses(List.of(to));
            return this;
        }

        public Builder replyTo(List<String> tos) {
            this.replyTo = makeAddresses(tos);
            return this;
        }

        public Builder recipient(String recipient) {
            this.recipients = makeAddresses(List.of(recipient));
            return this;
        }

        public Builder recipients(List<String> recipients) {
            this.recipients = makeAddresses(recipients);
            return this;
        }

        public Builder ccAddresses(List<String> ccAddresses) {
            this.ccAddresses = makeAddresses(ccAddresses);
            return this;
        }

        public Builder bccAddresses(List<String> bccAddresses) {
            this.bccAddresses = makeAddresses(bccAddresses);
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder textBodyEngine(String prefix, IDialect... dialects) {
            this.textBodyEngine = createTextEngine(prefix, dialects);
            return this;
        }

        public Builder textBodyEngine(ITemplateEngine textBodyTemplateEngine) {
            this.textBodyEngine = textBodyTemplateEngine;
            return this;
        }

        public Builder textBodyTemplate(String textBodyTemplate) {
            this.textBodyTemplate = textBodyTemplate;
            return this;
        }

        public Builder textBody(String textBody) {
            this.textBodyLiteral = textBody;
            return this;
        }

        public Builder htmlBodyEngine(String prefix, IDialect... dialects) {
            this.htmlBodyEngine = createHtmlEngine(prefix, dialects);
            return this;
        }

        public Builder htmlBodyEngine(ITemplateEngine htmlBodyTemplateEngine) {
            this.htmlBodyEngine = htmlBodyTemplateEngine;
            return this;
        }

        public Builder htmlBodyTemplate(String htmlBodyTemplate) {
            this.htmlBodyTemplate = htmlBodyTemplate;
            return this;
        }

        public Builder htmlBody(String htmlBody) {
            this.htmlBodyLiteral = htmlBody;
            return this;
        }

        public Builder htmlBodyPostprocessor(HtmlBodyPostprocessor htmlBodyPostprocessor) {
            this.htmlBodyPostprocessor = htmlBodyPostprocessor;
            return this;
        }

        public Builder cids(Collection<CidSource> cids) {
            this.cids.addAll(cids);
            return this;
        }

        public Builder cids(CidSource... cids) {
            this.cids.addAll(List.of(cids));
            return this;
        }

        public Builder attachments(Collection<AttachmentSource> attachments) {
            this.attachments.addAll(attachments);
            return this;
        }

        public Builder attachments(AttachmentSource... attachments) {
            this.attachments.addAll(List.of(attachments));
            return this;
        }

        public Builder variables(Map<String, Object> values) {
            this.variables.putAll(values);
            return this;
        }

        public Builder variable(String name, Object value) {
            this.variables.put(name, value);
            return this;
        }

        /**
         * When Application context is set, a ThymeleafEvaluationContext is
         * configured, and beans can be referenced from Spel expressions in
         * Thymeleaf.
         *
         * I.e.:
         * <pre>[[${@environment.getProperty('my.configuration')}]]</pre>
         *
         * @param applicationContext the application context
         * @return this builder
         */
        public Builder applicationContext(@Nullable ConfigurableApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
            return this;
        }

        public Builder spooling(EmailPaths paths, String prefix, ApplicationEventPublisher publisher) {
            Assert.notNull(paths, "paths must be non null");
            this.spooling = new Spooling(paths, prefix, publisher);
            return this;
        }

        public Prototype prototype() {
            return this;
        }

        @Override
        public Builder builder() {
            final var builder = new Builder();
            builder.messageId = messageId;
            builder.sender = sender;
            builder.replyTo = replyTo;
            builder.recipients = recipients;
            builder.ccAddresses = ccAddresses;
            builder.bccAddresses = bccAddresses;

            builder.subject = subject;

            builder.textBodyEngine = textBodyEngine;
            builder.textBodyTemplate = textBodyTemplate;
            builder.textBodyLiteral = textBodyLiteral;

            builder.htmlBodyEngine = htmlBodyEngine;
            builder.htmlBodyTemplate = htmlBodyTemplate;
            builder.htmlBodyPostprocessor = htmlBodyPostprocessor;
            builder.htmlBodyLiteral = htmlBodyLiteral;

            builder.attachments = new ArrayList<>(attachments);
            builder.cids = new ArrayList<>(cids);
            builder.variables = new HashMap<>(variables);
            builder.applicationContext = applicationContext;
            builder.spooling = spooling;
            return builder;
        }

        private static Context makeContext(ConfigurableApplicationContext ac, Map<String, Object> variables) {
            final var ctx = new Context();
            if (ac != null) {
                ctx.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME, new ThymeleafEvaluationContext(ac, ac.getBeanFactory().getConversionService()));
            }
            variables.forEach((k, v) -> ctx.setVariable(k, v));
            return ctx;
        }

        public static SpringTemplateEngine createTextEngine(String prefix, IDialect... dialects) {
            final var resolver = new ClassLoaderTemplateResolver();
            resolver.setOrder(1);
            resolver.setResolvablePatterns(Set.of("*.txt"));
            resolver.setPrefix(prefix);
            resolver.setTemplateMode(TemplateMode.TEXT);
            resolver.setCharacterEncoding("utf-8");
            resolver.setCacheable(true);

            final var engine = new SpringTemplateEngine();
            engine.addTemplateResolver(resolver);
            for (IDialect dialect : dialects) {
                engine.addDialect(dialect);
            }
            return engine;
        }

        public static SpringTemplateEngine createHtmlEngine(String prefix, IDialect... dialects) {
            final var resolver = new ClassLoaderTemplateResolver();
            resolver.setOrder(1);
            resolver.setResolvablePatterns(Set.of("*.html"));
            resolver.setPrefix(prefix);
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCharacterEncoding("utf-8");
            resolver.setCacheable(true);

            final var engine = new SpringTemplateEngine();
            engine.addTemplateResolver(resolver);
            for (IDialect dialect : dialects) {
                engine.addDialect(dialect);
            }
            return engine;
        }

        public EmailMessage build() {
            Assert.notNull(sender, "sender must be configured");
            Assert.isTrue(recipients != null && recipients.length > 0, "recipients must be configured and non empty");
            Assert.notNull(subject, "subject must be configured");

            final var htmlBodyTemplateConfigured = htmlBodyTemplate != null && htmlBodyEngine != null;
            final var textBodyTemplateConfigured = textBodyTemplate != null && textBodyEngine != null;

            Assert.isTrue(textBodyLiteral != null || htmlBodyLiteral != null || textBodyTemplateConfigured || htmlBodyTemplateConfigured, "at least one of textBody,htmlBody,(textBodyTemplate,textBodyTemplateEngine),(htmlBodyTemplate,htmlBodyTemplateEngine) must be configured");

            final var templated = htmlBodyTemplateConfigured || textBodyTemplateConfigured;

            final var context = templated ? makeContext(applicationContext, variables) : null;
            final var htmlBody = htmlBodyTemplateConfigured ? htmlBodyEngine.process(htmlBodyTemplate, context) : htmlBodyLiteral;
            final var postprocessedHtmlBody = htmlBodyPostprocessor != null ? htmlBodyPostprocessor.postprocess(htmlBody) : htmlBody;
            final var textBody = textBodyTemplateConfigured ? textBodyEngine.process(textBodyTemplate, context) : textBodyLiteral;

            return new EmailMessage(
                    messageId != null ? messageId : UUID.randomUUID().toString(),
                    sender,
                    replyTo != null ? replyTo : new InternetAddress[]{sender},
                    recipients,
                    ccAddresses != null ? ccAddresses : new InternetAddress[0],
                    bccAddresses != null ? bccAddresses : new InternetAddress[0],
                    subject,
                    textBody,
                    postprocessedHtmlBody,
                    attachments != null ? attachments : List.of(),
                    cids != null ? cids : List.of(),
                    spooling
            );
        }

        public Path marshal(Path path) {
            return new EmailMarshaller().marshal(build(), path);
        }

        public byte[] marshal() {
            return new EmailMarshaller().marshal(build());
        }

        public void marshal(OutputStream os) {
            new EmailMarshaller().marshal(build(), os);
        }

        public Path marshalToSpool() {
            Assert.notNull(spooling, "spooling must be configured to marshal to spool");
            final var p = new EmailMarshaller().marshalToSpool(build(), spooling.paths(), spooling.prefix());
            if (spooling.publisher() != null) {
                spooling.publisher().publishEvent(new EmailSpooled());
            }
            return p;
        }

    }
}
