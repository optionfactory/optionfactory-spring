package net.optionfactory.spring.email;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.thymeleaf.exceptions.TemplateEngineException;
import org.thymeleaf.templatemode.TemplateMode;

public class EmailMessageBuilderTest {

    private static final String TEMPLATES = "/net/optionfactory/spring/email/templates/";

    @TempDir
    Path tmp;

    private static EmailMessage.Builder minimal() {
        return EmailMessage.builder()
                .sender("sender@example.com", "Sender")
                .recipient("recipient@example.com")
                .subject("subject")
                .textBody("text");
    }

    private static AttachmentSource attachment(String name) {
        return AttachmentSource.of(new ByteArrayResource(new byte[]{1}), name, "application/octet-stream");
    }

    @Test
    public void buildFailsWhenAMandatorySettingIsMissing() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> EmailMessage.builder()
                .recipient("recipient@example.com").subject("subject").textBody("text").build(), "the sender is mandatory");
        Assertions.assertThrows(IllegalArgumentException.class, () -> EmailMessage.builder()
                .sender("sender@example.com", null).subject("subject").textBody("text").build(), "a recipient is mandatory");
        Assertions.assertThrows(IllegalArgumentException.class, () -> minimal().recipients(List.of()).build(), "an empty recipient list is rejected");
        Assertions.assertThrows(IllegalArgumentException.class, () -> EmailMessage.builder()
                .sender("sender@example.com", null).recipient("recipient@example.com").textBody("text").build(), "the subject is mandatory");
        Assertions.assertThrows(IllegalArgumentException.class, () -> EmailMessage.builder()
                .sender("sender@example.com", null).recipient("recipient@example.com").subject("subject").build(), "a body is mandatory");
    }

    @Test
    public void aTemplateWithoutAnEngineDoesNotCountAsABody() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> EmailMessage.builder()
                .sender("sender@example.com", null)
                .recipient("recipient@example.com")
                .subject("subject")
                .htmlBodyTemplate("greeting.html")
                .build(), "a template is only rendered with its engine");
    }

    @Test
    public void unsetOptionalSettingsGetTheirDefaults() {
        final var m = minimal().build();

        Assertions.assertArrayEquals(new Object[]{m.sender()}, m.replyTo(), "replies go to the sender by default");
        Assertions.assertEquals(0, m.ccAddresses().length, "no cc by default");
        Assertions.assertEquals(0, m.bccAddresses().length, "no bcc by default");
        Assertions.assertNull(m.htmlBody(), "a message with a text body only has no html body");
        Assertions.assertNull(m.spoolConfig(), "no spooling unless configured");
    }

    @Test
    public void aMessageIdIsGeneratedAtEachBuildUnlessSet() {
        final var builder = minimal();

        Assertions.assertNotEquals(builder.build().messageId(), builder.build().messageId(), "every build generates a new message id");
        Assertions.assertEquals("my-id", builder.messageId("my-id").build().messageId(), "an explicit message id is kept");
    }

    @Test
    public void singleValueSettersReplaceWhileCollectionSettersAccumulate() {
        final var m = minimal()
                .recipients(List.of("a@example.com", "b@example.com"))
                .recipient("c@example.com")
                .attachments(attachment("1.bin"))
                .attachments(List.of(attachment("2.bin")))
                .build();

        Assertions.assertEquals(1, m.recipients().length, "recipient replaces the recipients set before");
        Assertions.assertEquals("c@example.com", m.recipients()[0].getAddress(), "the last recipient set is kept");
        Assertions.assertEquals(List.of("1.bin", "2.bin"), m.attachments().stream().map(AttachmentSource::fileName).toList(), "attachments add to those set before");
    }

    @Test
    public void aTemplateWinsOverALiteralBody() {
        final var m = minimal()
                .htmlBody("<p>literal</p>")
                .htmlBodyEngine(f -> f.html(TEMPLATES, null))
                .htmlBodyTemplate("greeting.html")
                .variable("name", "Jane")
                .build();

        Assertions.assertEquals("<p>Hello Jane</p>\n", m.htmlBody(), "the template is rendered instead of the literal");
    }

    @Test
    public void textTemplatesAreRenderedWithTheVariables() {
        final var m = minimal()
                .textBodyEngine(f -> f.text(TEMPLATES, null))
                .textBodyTemplate("greeting.txt")
                .variables(Map.of("name", "Jane"))
                .build();

        Assertions.assertEquals("Hello Jane\n", m.textBody(), "the text template is rendered with the variables");
    }

    @Test
    public void stringTemplatesAreTheTemplateSourceItself() {
        final var m = minimal()
                .htmlBodyEngine(f -> f.string(TemplateMode.HTML, null))
                .htmlBodyTemplate("<b>[[${name}]]</b>")
                .variable("name", "Jane")
                .build();

        Assertions.assertEquals("<b>Jane</b>", m.htmlBody(), "the template name is the template content");
    }

    @Test
    public void theHtmlEngineOnlyResolvesHtmlTemplates() {
        final var builder = minimal()
                .htmlBodyEngine(f -> f.html(TEMPLATES, null))
                .htmlBodyTemplate("greeting.txt")
                .variable("name", "Jane");

        Assertions.assertThrows(TemplateEngineException.class, builder::build, "a name not ending in .html is not resolved by the html engine");
    }

    @Test
    public void thePostprocessorIsAppliedToTheHtmlBody() {
        final var m = minimal()
                .htmlBody("<p>body</p>")
                .htmlBodyPostprocessor(html -> html.toUpperCase())
                .build();

        Assertions.assertEquals("<P>BODY</P>", m.htmlBody(), "the html body is the postprocessed one");
        Assertions.assertEquals("text", m.textBody(), "the text body is not postprocessed");
    }

    @Test
    public void buildersFromAPrototypeAreIndependent() {
        final var prototype = minimal()
                .attachments(attachment("common.bin"))
                .variable("common", 1)
                .prototype();

        final var first = prototype.builder()
                .recipient("first@example.com")
                .attachments(attachment("first.bin"))
                .build();
        final var second = prototype.builder().build();

        Assertions.assertEquals(List.of("common.bin", "first.bin"), first.attachments().stream().map(AttachmentSource::fileName).toList(), "a derived builder starts from the prototype's attachments");
        Assertions.assertEquals(List.of("common.bin"), second.attachments().stream().map(AttachmentSource::fileName).toList(), "what a derived builder adds does not leak into the prototype");
        Assertions.assertEquals("recipient@example.com", second.recipients()[0].getAddress(), "what a derived builder replaces does not leak into the prototype");
    }

    @Test
    public void marshalToSpoolRequiresTheSpoolingConfiguration() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> minimal().marshalToSpool(), "a message without spooling cannot be spooled");
    }

    @Test
    public void marshalToSpoolWritesTheEmailThenPublishesEmailSpooled() throws Exception {
        final var paths = EmailPaths.provide(tmp.resolve("spool"), null, null);
        final var events = new ArrayList<Object>();

        final var spooled = minimal()
                .spooling(paths, "welcome.", event -> {
                    Assertions.assertEquals(1, countEmls(paths.spool()), "the event is published once the email is in the spool");
                    events.add(event);
                })
                .marshalToSpool();

        Assertions.assertEquals(paths.spool(), spooled.getParent(), "the email is written into the spool");
        Assertions.assertTrue(spooled.getFileName().toString().startsWith("welcome."), "the spooled file is named after the prefix");
        Assertions.assertEquals(1, events.size(), "one event per spooled email");
        Assertions.assertInstanceOf(EmailSpooled.class, events.get(0), "the published event is EmailSpooled");
    }

    @Test
    public void marshalToSpoolWithoutPublisherPublishesNothing() throws Exception {
        final var paths = EmailPaths.provide(tmp.resolve("spool"), null, null);

        final var spooled = minimal().spooling(paths, null, null).marshalToSpool();

        Assertions.assertTrue(Files.exists(spooled), "the email is spooled even without a publisher");
    }

    @Test
    public void spoolingRequiresThePaths() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> minimal().spooling(null, "prefix", null), "spooling without paths is rejected right away");
    }

    private static long countEmls(Path dir) {
        try (var files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".eml")).count();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
