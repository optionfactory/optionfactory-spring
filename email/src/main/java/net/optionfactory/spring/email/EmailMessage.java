package net.optionfactory.spring.email;

import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.email.marshaller.EmailMarshaller.EmailMarshallingException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

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
        List<CidSource> cids) {

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

        private ITemplateEngine subjectTemplateEngine;
        private String subjectTemplate;
        private String subjectLiteral;
        private ITemplateEngine textBodyTemplateEngine;
        private String textBodyTemplate;
        private String textBodyLiteral;
        private ITemplateEngine htmlBodyTemplateEngine;
        private String htmlBodyTemplate;
        private String htmlBodyLiteral;
        private List<AttachmentSource> attachments = new ArrayList<>();
        private List<CidSource> cids = new ArrayList<>();

        private Map<String, Object> variables = new ConcurrentHashMap<>();

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

        public Builder subjectTemplateEngine(IDialect... dialects) {
            final var resolver = new StringTemplateResolver();
            resolver.setOrder(1);
            resolver.setTemplateMode(TemplateMode.TEXT);
            resolver.setCacheable(true);

            final var engine = new SpringTemplateEngine();
            engine.addTemplateResolver(resolver);
            for (IDialect dialect : dialects) {
                engine.addDialect(dialect);
            }

            this.subjectTemplateEngine = engine;
            return this;
        }

        public Builder subjectTemplateEngine(ITemplateEngine subjectTemplateEngine) {
            this.subjectTemplateEngine = subjectTemplateEngine;
            return this;
        }

        public Builder subjectTemplate(String subjectTemplate) {
            this.subjectTemplate = subjectTemplate;
            return this;
        }

        public Builder subject(String subject) {
            this.subjectLiteral = subject;
            return this;
        }

        public Builder textBodyTemplateEngine(String prefix, IDialect... dialects) {
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

            this.textBodyTemplateEngine = engine;
            return this;
        }

        public Builder textBodyTemplateEngine(ITemplateEngine textBodyTemplateEngine) {
            this.textBodyTemplateEngine = textBodyTemplateEngine;
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

        public Builder htmlBodyTemplateEngine(String prefix, IDialect... dialects) {
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

            this.htmlBodyTemplateEngine = engine;
            return this;
        }

        public Builder htmlBodyTemplateEngine(ITemplateEngine htmlBodyTemplateEngine) {
            this.htmlBodyTemplateEngine = htmlBodyTemplateEngine;
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

            builder.subjectTemplateEngine = subjectTemplateEngine;
            builder.subjectTemplate = subjectTemplate;
            builder.subjectLiteral = subjectLiteral;

            builder.textBodyTemplateEngine = textBodyTemplateEngine;
            builder.textBodyTemplate = textBodyTemplate;
            builder.textBodyLiteral = textBodyLiteral;

            builder.htmlBodyTemplateEngine = htmlBodyTemplateEngine;
            builder.htmlBodyTemplate = htmlBodyTemplate;
            builder.htmlBodyLiteral = htmlBodyLiteral;

            builder.attachments = new ArrayList<>(attachments);
            builder.cids = new ArrayList<>(cids);
            builder.variables = new ConcurrentHashMap<>(variables);
            return builder;
        }

        private static Context makeContext(Map<String, Object> variables) {
            final var ctx = new Context();
            variables.forEach((k, v) -> ctx.setVariable(k, v));
            return ctx;
        }

        public EmailMessage build() {
            Assert.notNull(sender, "sender must be configured");
            Assert.notNull(recipients, "recipients must be configured");

            //both template and engine must be configured, otherwise we fallback to literal, which can be null for bodies
            final var subjectTemplateConfigued = subjectTemplate != null && subjectTemplateEngine != null;
            final var htmlBodyTemplateConfigured = htmlBodyTemplate != null && htmlBodyTemplateEngine != null;
            final var textBodyTemplateConfigured = textBodyTemplate != null && textBodyTemplateEngine != null;

            Assert.isTrue(subjectLiteral != null || subjectTemplateConfigued, "at least one of subject,(subjectTemplate,subjectTemplateEngine) must be configured");
            Assert.isTrue(textBodyLiteral != null || htmlBodyLiteral != null || textBodyTemplateConfigured || htmlBodyTemplateConfigured, "at least one of textBody,htmlBody,(textBodyTemplate,textBodyTemplateEngine),(htmlBodyTemplate,htmlBodyTemplateEngine) must be configured");

            final var templated = subjectTemplateConfigued || htmlBodyTemplateConfigured || textBodyTemplateConfigured;

            final var context = templated ? makeContext(variables) : null;
            final var subject = subjectTemplateConfigued ? subjectTemplateEngine.process(subjectTemplate, context) : subjectLiteral;
            final var htmlBody = htmlBodyTemplateConfigured ? htmlBodyTemplateEngine.process(htmlBodyTemplate, context) : htmlBodyLiteral;
            final var textBody = textBodyTemplateConfigured ? textBodyTemplateEngine.process(textBodyTemplate, context) : textBodyLiteral;

            return new EmailMessage(
                    messageId != null ? messageId : UUID.randomUUID().toString(),
                    sender,
                    replyTo != null ? replyTo : new InternetAddress[]{sender},
                    recipients,
                    ccAddresses != null ? ccAddresses : new InternetAddress[0],
                    bccAddresses != null ? bccAddresses : new InternetAddress[0],
                    subject,
                    textBody,
                    htmlBody,
                    attachments != null ? attachments : List.of(),
                    cids != null ? cids : List.of()
            );
        }

    }
}
