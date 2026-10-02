package net.optionfactory.spring.email;

import jakarta.mail.Multipart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;

public class EmailMarshallerTest {

    @TempDir
    Path tmp;

    private final EmailMarshaller marshaller = new EmailMarshaller();
    private final ByteArrayResource png = new ByteArrayResource(new byte[]{(byte) 0x89, 'P', 'N', 'G'});

    private static EmailMessage.Builder minimal() {
        return EmailMessage.builder()
                .sender("sender@example.com", "Sender")
                .recipient("recipient@example.com")
                .subject("subject");
    }

    private static MimeMessage parse(byte[] eml) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(eml));
    }

    @Test
    public void aFullMessageNestsTheBodiesInsideAlternativesAndTheAttachmentsBesideThem() throws Exception {
        final var em = minimal()
                .textBody("text")
                .htmlBody("<img src='cid:logo'>")
                .cids(CidSource.of(png, "logo", "image/png"))
                .attachments(AttachmentSource.of(png, "report.png", "image/png"))
                .build();

        final var parsed = parse(marshaller.marshal(em));

        final var mixed = (Multipart) parsed.getContent();
        Assertions.assertTrue(parsed.isMimeType("multipart/mixed"), "the root is multipart/mixed");
        Assertions.assertEquals(2, mixed.getCount(), "the alternatives and one attachment");
        final var alternatives = (Multipart) mixed.getBodyPart(0).getContent();
        Assertions.assertTrue(mixed.getBodyPart(0).isMimeType("multipart/alternative"), "the bodies come first, as alternatives");
        Assertions.assertTrue(alternatives.getBodyPart(0).isMimeType("text/plain"), "the text body is the first, least preferred, alternative");
        Assertions.assertTrue(alternatives.getBodyPart(1).isMimeType("multipart/related"), "the html body and its resources are the last, preferred, alternative");
        final var related = (Multipart) alternatives.getBodyPart(1).getContent();
        Assertions.assertTrue(related.getBodyPart(0).isMimeType("text/html"), "the html body comes first in the related part");
        Assertions.assertArrayEquals(new String[]{"logo"}, related.getBodyPart(1).getHeader("Content-ID"), "the inline resource carries its content id");
        Assertions.assertEquals("report.png", mixed.getBodyPart(1).getFileName(), "the attachment carries its file name");
        Assertions.assertTrue(mixed.getBodyPart(1).isMimeType("image/png"), "the attachment carries its content type");
    }

    @Test
    public void aTextOnlyMessageDropsTheInlineResources() throws Exception {
        final var em = minimal()
                .textBody("text")
                .cids(CidSource.of(png, "logo", "image/png"))
                .build();

        final var parsed = parse(marshaller.marshal(em));

        final var alternatives = (Multipart) ((Multipart) parsed.getContent()).getBodyPart(0).getContent();
        Assertions.assertEquals(1, alternatives.getCount(), "only the text alternative, with no related part");
        Assertions.assertTrue(alternatives.getBodyPart(0).isMimeType("text/plain"), "the only alternative is the text body");
    }

    @Test
    public void headersCarryTheMessageIdAsIsAndUtf8Values() throws Exception {
        final var em = minimal()
                .messageId("my-id@example.com")
                .sender("sender@example.com", "Società")
                .subject("caffè ☕")
                .textBody("perché")
                .ccAddresses(List.of("cc@example.com"))
                .build();

        final var parsed = parse(marshaller.marshal(em));

        Assertions.assertEquals("<my-id@example.com>", parsed.getMessageID(), "the message id is kept, not regenerated on save");
        Assertions.assertEquals("caffè ☕", parsed.getSubject(), "the subject survives as UTF-8");
        Assertions.assertEquals("Società", ((InternetAddress) parsed.getFrom()[0]).getPersonal(), "the sender display name survives as UTF-8");
        Assertions.assertEquals("cc@example.com", parsed.getRecipients(MimeMessage.RecipientType.CC)[0].toString(), "cc addresses are marshalled");
        Assertions.assertEquals("sender@example.com", ((InternetAddress) parsed.getReplyTo()[0]).getAddress(), "the reply-to defaults to the sender");
        final var text = ((Multipart) ((Multipart) parsed.getContent()).getBodyPart(0).getContent()).getBodyPart(0);
        Assertions.assertEquals("perché", text.getContent(), "the text body survives as UTF-8");
    }

    @Test
    public void marshallingToAFileOverwritesItsPreviousContent() throws Exception {
        final var target = Files.writeString(tmp.resolve("out.eml"), "stale".repeat(10_000));

        final var returned = marshaller.marshal(minimal().textBody("text").build(), target);

        Assertions.assertEquals(target, returned, "the written path is returned");
        Assertions.assertFalse(Files.readString(target).contains("stale"), "the previous content is truncated away");
    }

    @Test
    public void marshalToSpoolLeavesOnlyTheEmlInTheSpool() throws Exception {
        final var paths = EmailPaths.provide(tmp.resolve("spool"), null, null);

        final var spooled = marshaller.marshalToSpool(minimal().textBody("text").build(), paths, "prefix.");

        try (var files = Files.list(paths.spool())) {
            Assertions.assertEquals(List.of(spooled), files.toList(), "only the renamed .eml remains, no .tmp");
        }
        Assertions.assertTrue(spooled.getFileName().toString().matches("prefix\\..+\\.eml"), "the spooled file is named after the prefix and ends in .eml");
        Assertions.assertEquals("subject", parse(Files.readAllBytes(spooled)).getSubject(), "the spooled file is the marshalled email");
    }

    @Test
    public void marshallingToAnUnwritablePathFails() {
        final var missingDir = tmp.resolve("missing").resolve("out.eml");

        Assertions.assertThrows(EmailMarshaller.EmailMarshallingException.class, () -> marshaller.marshal(minimal().textBody("text").build(), missingDir), "a file that cannot be created fails the marshalling");
    }

    @Test
    public void inputStreamSourceDataSourceIsReadOnly() throws Exception {
        final var ds = new EmailMarshaller.InputStreamSourceDataSource(png, "image/png");

        Assertions.assertArrayEquals(png.getByteArray(), ds.getInputStream().readAllBytes(), "reads return the source content");
        Assertions.assertEquals("image/png", ds.getContentType(), "the content type is the given one");
        Assertions.assertThrows(UnsupportedOperationException.class, ds::getOutputStream, "the data source cannot be written to");
    }

    @Test
    public void inlineResourcesAndAttachmentsAreStreamedAtMarshallingTime() throws Exception {
        final var reads = new AtomicInteger();
        final InputStreamSource counting = () -> {
            reads.incrementAndGet();
            return new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));
        };
        final var em = minimal().textBody("text").attachments(AttachmentSource.of(counting, "a.txt", "text/plain")).build();

        Assertions.assertEquals(0, reads.get(), "building the message does not read the attachment");
        marshaller.marshal(em);
        final var afterFirst = reads.get();
        marshaller.marshal(em);

        Assertions.assertTrue(afterFirst > 0, "marshalling reads the attachment");
        Assertions.assertTrue(reads.get() > afterFirst, "every marshalling reads the attachment again");
    }
}
