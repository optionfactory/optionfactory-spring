package net.optionfactory.spring.email;

import jakarta.mail.internet.InternetAddress;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.optionfactory.spring.email.EmailMarshaller.EmailMarshallingException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.util.Assert;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/// An email ready to be marshalled: addresses resolved, bodies rendered, attachments referenced.
///
/// Messages are built with a [Builder], usually derived from a [Prototype] holding everything
/// common to a kind of email (sender, template, spooling), so that each email only adds what is
/// specific to it:
///
/// ```java
/// @Bean
/// public EmailMessage.Prototype welcomeEmail(ConfigurableApplicationContext ac, EmailPaths paths) {
///     return EmailMessage.builder()
///             .sender("noreply@example.com", "Example")
///             .subject("Welcome")
///             .htmlBodyEngine(f -> f.html("/emails/", ac))
///             .htmlBodyTemplate("welcome.html")
///             .htmlBodyPostprocessor(new CssInliner())
///             .spooling(paths, "welcome.", ac)
///             .prototype();
/// }
///
/// welcomeEmail.builder()
///         .recipient(user.email())
///         .locale(user.locale())
///         .variable("user", user)
///         .marshalToSpool();
/// ```
///
/// @param messageId the `Message-ID`, without angle brackets
/// @param sender the `From` address
/// @param replyTo the `Reply-To` addresses
/// @param recipients the `To` addresses, never empty
/// @param ccAddresses the `Cc` addresses, possibly empty
/// @param bccAddresses the `Bcc` addresses, possibly empty
/// @param subject the subject
/// @param textBody the plain text body, `null` when the message has an html body only
/// @param htmlBody the html body, `null` when the message has a text body only
/// @param attachments the attachments, possibly empty
/// @param cids the resources the html body references by content id, possibly empty
/// @param spoolConfig where [Builder#marshalToSpool()] writes the message, `null` when not
/// configured
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

    /// Where a message is spooled, and who is told about it.
    ///
    /// @param paths the spool to write into
    /// @param prefix the spooled file name prefix, may be `null`
    /// @param publisher receives an [EmailSpooled] event after each email is spooled, `null` to
    /// publish nothing
    public record Spooling(
            @NonNull
            EmailPaths paths,
            @Nullable
            String prefix,
            @Nullable
            ApplicationEventPublisher publisher) {

    }

    /// @return an empty builder
    public static Builder builder() {
        return new Builder();
    }

    /// The common part of a kind of email, from which each email of that kind is built.
    ///
    /// Each call to [#builder()] returns an independent copy, so the email-specific settings never
    /// leak into the prototype or into other emails, and a prototype that nothing mutates can be
    /// shared between threads. What the copies share must then be thread-safe too: the template
    /// engines are, the [net.optionfactory.spring.email.inliner.CssInliner] is not.
    public interface Prototype {

        /// The copy is shallow: lists of attachments, cids and the variables are copied, while the
        /// template engines, the postprocessor and the values themselves are shared.
        ///
        /// @return a new builder holding the prototype's configuration
        Builder builder();
    }

    /// Creates the thymeleaf engines that render email bodies, meant to be used through
    /// [Builder#htmlBodyEngine(Function)] and [Builder#textBodyEngine(Function)].
    ///
    /// The engines cache the parsed templates, so an engine should be created once per kind of
    /// email (e.g. in a [Prototype]) rather than once per email. When the message source is `null`,
    /// `#{...}` messages are looked up with thymeleaf's standard resolution, from properties files
    /// next to the template.
    public enum TemplateEngineFactory {
        /// The only instance.
        INSTANCE;

        /// Creates an engine whose templates are the template sources themselves: the
        /// "template name" passed to [Builder#htmlBodyTemplate] or [Builder#textBodyTemplate] is
        /// the template content.
        ///
        /// Unlike [#text] and [#html], this method does not register the given `dialects`: they are
        /// ignored.
        ///
        /// @param mode the template mode, e.g. `TemplateMode.HTML`
        /// @param ms the source of `#{...}` messages, may be `null`
        /// @param dialects ignored
        /// @return the template engine
        public SpringTemplateEngine string(TemplateMode mode, @Nullable MessageSource ms, IDialect... dialects) {
            final var resolver = new StringTemplateResolver();
            resolver.setOrder(1);
            resolver.setTemplateMode(mode);
            resolver.setCacheable(true);
            final var engine = new SpringTemplateEngine();
            engine.addTemplateResolver(resolver);
            engine.setTemplateEngineMessageSource(ms);
            return engine;
        }

        /// Creates an engine rendering `TEXT` templates loaded from the classpath. Only names
        /// ending in `.txt` are resolved: any other name fails the build of the message.
        ///
        /// @param prefix the classpath location of the templates, e.g. `/emails/`
        /// @param ms the source of `#{...}` messages, may be `null`
        /// @param dialects further dialects to register, e.g. for custom expression objects
        /// @return the template engine
        public SpringTemplateEngine text(String prefix, @Nullable MessageSource ms, IDialect... dialects) {
            final var resolver = new ClassLoaderTemplateResolver();
            resolver.setOrder(1);
            resolver.setResolvablePatterns(Set.of("*.txt"));
            resolver.setPrefix(prefix);
            resolver.setTemplateMode(TemplateMode.TEXT);
            resolver.setCharacterEncoding("utf-8");
            resolver.setCacheable(true);

            final var engine = new SpringTemplateEngine();
            engine.addTemplateResolver(resolver);
            engine.setTemplateEngineMessageSource(ms);
            for (IDialect dialect : dialects) {
                engine.addDialect(dialect);
            }
            return engine;
        }

        /// Creates an engine rendering `HTML` templates loaded from the classpath. Only names
        /// ending in `.html` are resolved: any other name fails the build of the message.
        ///
        /// @param prefix the classpath location of the templates, e.g. `/emails/`
        /// @param ms the source of `#{...}` messages, may be `null`
        /// @param dialects further dialects to register, e.g. for custom expression objects
        /// @return the template engine
        public SpringTemplateEngine html(String prefix, @Nullable MessageSource ms, IDialect... dialects) {
            final var resolver = new ClassLoaderTemplateResolver();
            resolver.setOrder(1);
            resolver.setResolvablePatterns(Set.of("*.html"));
            resolver.setPrefix(prefix);
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCharacterEncoding("utf-8");
            resolver.setCacheable(true);

            final var engine = new SpringTemplateEngine();
            engine.addTemplateResolver(resolver);
            engine.setTemplateEngineMessageSource(ms);
            for (IDialect dialect : dialects) {
                engine.addDialect(dialect);
            }
            return engine;
        }

    }

    /// Builds an [EmailMessage], and marshals it.
    ///
    /// `sender`, at least one recipient, `subject` and at least one body are mandatory. Each body
    /// is either a literal or a template rendered with its engine; when both are configured the
    /// template wins, and a template without an engine is ignored. Template variables, the locale
    /// and the application context are only used to render templates.
    ///
    /// Setters for a single value replace it, so `recipient` after `recipients` leaves one
    /// recipient, while `attachments`, `cids` and `variables` add to what is already there.
    ///
    /// Addresses are not validated, neither here nor when marshalling.
    ///
    /// A builder is not thread-safe. Once [#prototype()] has been called it must no longer be
    /// modified, since the prototype is the builder itself.
    public static class Builder implements Prototype {

        private String messageId;
        private InternetAddress sender;
        private InternetAddress[] replyTo;
        private InternetAddress[] recipients;
        private InternetAddress[] ccAddresses;
        private InternetAddress[] bccAddresses;

        private Locale locale;
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

        /// @param messageId the `Message-ID`, without angle brackets; a random UUID is generated at
        /// each build when not set
        /// @return this builder
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

        /// @param sender the `From` address, also the default `Reply-To`
        /// @param description the display name shown by mail clients, may be `null`
        /// @return this builder
        public Builder sender(String sender, @Nullable String description) {
            this.sender = makeAddress(sender, description);
            return this;
        }

        /// @param to the only `Reply-To` address
        /// @return this builder
        public Builder replyTo(String to) {
            this.replyTo = makeAddresses(List.of(to));
            return this;
        }

        /// @param tos the `Reply-To` addresses, replacing the default of replying to the sender
        /// @return this builder
        public Builder replyTo(List<String> tos) {
            this.replyTo = makeAddresses(tos);
            return this;
        }

        /// @param recipient the only `To` address
        /// @return this builder
        public Builder recipient(String recipient) {
            this.recipients = makeAddresses(List.of(recipient));
            return this;
        }

        /// @param recipients the `To` addresses, which must not be empty when building
        /// @return this builder
        public Builder recipients(List<String> recipients) {
            this.recipients = makeAddresses(recipients);
            return this;
        }

        /// @param ccAddresses the `Cc` addresses
        /// @return this builder
        public Builder ccAddresses(List<String> ccAddresses) {
            this.ccAddresses = makeAddresses(ccAddresses);
            return this;
        }

        /// @param bccAddresses the `Bcc` addresses
        /// @return this builder
        public Builder bccAddresses(List<String> bccAddresses) {
            this.bccAddresses = makeAddresses(bccAddresses);
            return this;
        }

        /// @param locale the locale templates are rendered in, e.g. for `#{...}` messages; the JVM
        /// default locale when `null`
        /// @return this builder
        public Builder locale(@Nullable Locale locale) {
            this.locale = locale;
            return this;
        }
        
        /// @param subject the subject, used as is: it is not a template
        /// @return this builder
        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        /// The function is invoked right away, so the engine is created once and shared by every
        /// builder derived from this one.
        ///
        /// @param customizer creates the engine from the [TemplateEngineFactory]
        /// @return this builder
        public Builder textBodyEngine(Function<TemplateEngineFactory, ITemplateEngine> customizer) {
            this.textBodyEngine = customizer.apply(TemplateEngineFactory.INSTANCE);
            return this;
        }

        /// @param textBodyTemplateEngine renders the text body template
        /// @return this builder
        public Builder textBodyEngine(ITemplateEngine textBodyTemplateEngine) {
            this.textBodyEngine = textBodyTemplateEngine;
            return this;
        }

        /// @param textBodyTemplate the template name, as resolved by the text body engine
        /// @return this builder
        public Builder textBodyTemplate(String textBodyTemplate) {
            this.textBodyTemplate = textBodyTemplate;
            return this;
        }

        /// @param textBody the literal text body, used when no text body template is configured
        /// @return this builder
        public Builder textBody(String textBody) {
            this.textBodyLiteral = textBody;
            return this;
        }

        /// The function is invoked right away, so the engine is created once and shared by every
        /// builder derived from this one.
        ///
        /// @param customizer creates the engine from the [TemplateEngineFactory]
        /// @return this builder
        public Builder htmlBodyEngine(Function<TemplateEngineFactory, ITemplateEngine> customizer) {
            this.htmlBodyEngine = customizer.apply(TemplateEngineFactory.INSTANCE);
            return this;
        }

        /// @param htmlBodyTemplateEngine renders the html body template
        /// @return this builder
        public Builder htmlBodyEngine(ITemplateEngine htmlBodyTemplateEngine) {
            this.htmlBodyEngine = htmlBodyTemplateEngine;
            return this;
        }

        /// @param htmlBodyTemplate the template name, as resolved by the html body engine
        /// @return this builder
        public Builder htmlBodyTemplate(String htmlBodyTemplate) {
            this.htmlBodyTemplate = htmlBodyTemplate;
            return this;
        }

        /// @param htmlBody the literal html body, used when no html body template is configured
        /// @return this builder
        public Builder htmlBody(String htmlBody) {
            this.htmlBodyLiteral = htmlBody;
            return this;
        }

        /// The postprocessor is applied to the html body, literal or rendered, at each build. It is
        /// also invoked, with `null`, for a message without an html body: see
        /// [HtmlBodyPostprocessor].
        ///
        /// @param htmlBodyPostprocessor transforms the html body, e.g. a
        /// [net.optionfactory.spring.email.inliner.CssInliner]
        /// @return this builder
        public Builder htmlBodyPostprocessor(HtmlBodyPostprocessor htmlBodyPostprocessor) {
            this.htmlBodyPostprocessor = htmlBodyPostprocessor;
            return this;
        }

        /// @param cids resources to add, referenced by the html body
        /// @return this builder
        public Builder cids(Collection<CidSource> cids) {
            this.cids.addAll(cids);
            return this;
        }

        /// @param cids resources to add, referenced by the html body
        /// @return this builder
        public Builder cids(CidSource... cids) {
            this.cids.addAll(List.of(cids));
            return this;
        }

        /// @param attachments attachments to add
        /// @return this builder
        public Builder attachments(Collection<AttachmentSource> attachments) {
            this.attachments.addAll(attachments);
            return this;
        }

        /// @param attachments attachments to add
        /// @return this builder
        public Builder attachments(AttachmentSource... attachments) {
            this.attachments.addAll(List.of(attachments));
            return this;
        }

        /// @param values template variables to add, replacing those with the same name
        /// @return this builder
        public Builder variables(Map<String, Object> values) {
            this.variables.putAll(values);
            return this;
        }

        /// @param name the template variable name
        /// @param value the template variable value
        /// @return this builder
        public Builder variable(String name, Object value) {
            this.variables.put(name, value);
            return this;
        }

        /// Makes the beans of the application context available to the SpEL expressions of the
        /// templates, e.g.:
        ///
        /// ```html
        /// [[${@environment.getProperty('my.configuration')}]]
        /// ```
        ///
        /// The context's conversion service is used as well.
        ///
        /// @param applicationContext the application context, `null` to expose no beans
        /// @return this builder
        public Builder expressions(@Nullable ConfigurableApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
            return this;
        }

        /// Configures where [#marshalToSpool()] writes the message.
        ///
        /// @param paths the spool to write into
        /// @param prefix the spooled file name prefix, may be `null`
        /// @param publisher receives an [EmailSpooled] event after each email is spooled, typically
        /// the application context so that a [ScheduledEmailSender] sends it right away; `null` to
        /// publish nothing
        /// @return this builder
        /// @throws IllegalArgumentException when `paths` is `null`
        public Builder spooling(EmailPaths paths, String prefix, ApplicationEventPublisher publisher) {
            Assert.notNull(paths, "paths must be non null");
            this.spooling = new Spooling(paths, prefix, publisher);
            return this;
        }

        /// @return this builder, seen as a prototype
        public Prototype prototype() {
            return this;
        }

        /// @return an independent copy of this builder, see [Prototype#builder()]
        @Override
        public Builder builder() {
            final var builder = new Builder();
            builder.messageId = messageId;
            builder.sender = sender;
            builder.replyTo = replyTo;
            builder.recipients = recipients;
            builder.ccAddresses = ccAddresses;
            builder.bccAddresses = bccAddresses;

            builder.locale = locale;
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

        private static Context makeContext(ConfigurableApplicationContext ac, Locale locale, Map<String, Object> variables) {
            final var ctx = new Context(locale);
            if (ac != null) {
                ctx.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME, new ThymeleafEvaluationContext(ac, ac.getBeanFactory().getConversionService()));
            }
            variables.forEach((k, v) -> ctx.setVariable(k, v));
            return ctx;
        }

        /// Renders the templates and builds the message. Every build renders anew, and generates a
        /// new message id unless one was set.
        ///
        /// @return the message
        /// @throws IllegalArgumentException when a mandatory setting is missing
        /// @throws org.thymeleaf.exceptions.TemplateEngineException when a template cannot be
        /// resolved or rendered
        public EmailMessage build() {
            Assert.notNull(sender, "sender must be configured");
            Assert.isTrue(recipients != null && recipients.length > 0, "recipients must be configured and non empty");
            Assert.notNull(subject, "subject must be configured");

            final var htmlBodyTemplateConfigured = htmlBodyTemplate != null && htmlBodyEngine != null;
            final var textBodyTemplateConfigured = textBodyTemplate != null && textBodyEngine != null;

            Assert.isTrue(textBodyLiteral != null || htmlBodyLiteral != null || textBodyTemplateConfigured || htmlBodyTemplateConfigured, "at least one of textBody,htmlBody,(textBodyTemplate,textBodyTemplateEngine),(htmlBodyTemplate,htmlBodyTemplateEngine) must be configured");

            final var templated = htmlBodyTemplateConfigured || textBodyTemplateConfigured;

            final var context = templated ? makeContext(applicationContext, locale, variables) : null;
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

        /// Builds the message and marshals it to a file, see [EmailMarshaller#marshal(EmailMessage, Path)].
        ///
        /// @param path the file to write, created or overwritten
        /// @return `path`
        public Path marshal(Path path) {
            return new EmailMarshaller().marshal(build(), path);
        }

        /// Builds the message and marshals it in memory, e.g. to inspect it in a test.
        ///
        /// @return the marshalled message
        public byte[] marshal() {
            return new EmailMarshaller().marshal(build());
        }

        /// Builds the message and marshals it to a stream.
        ///
        /// @param os where the message is written, left open
        public void marshal(OutputStream os) {
            new EmailMarshaller().marshal(build(), os);
        }

        /// Builds the message, writes it to the configured spool, then publishes an [EmailSpooled]
        /// event when the spooling configuration has a publisher.
        ///
        /// @return the spooled file
        /// @throws IllegalArgumentException when [#spooling] has not been configured
        /// @see EmailMarshaller#marshalToSpool
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
