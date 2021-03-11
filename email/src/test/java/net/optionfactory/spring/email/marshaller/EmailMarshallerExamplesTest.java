package net.optionfactory.spring.email.marshaller;


import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailSenderAndCopyAddresses;
import net.optionfactory.spring.email.marshaller.EmailMarshaller.AttachmentSource;
import net.optionfactory.spring.email.marshaller.EmailMarshaller.CidSource;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class EmailMarshallerExamplesTest {

    private final EmailMarshaller m = new EmailMarshaller();
    private final Resource icon = new ClassPathResource("icon.png", EmailMarshallerExamplesTest.class);

    @Test
    public void canMarshalCidsAttachmentsTextAndHtml() {

        final EmailSenderAndCopyAddresses config = new EmailSenderAndCopyAddresses();
        config.sender = "test.sender@example.com";
        config.senderDescription = "Test Sender";
        config.ccAddresses = List.of();
        config.bccAddresses = List.of();

        final var message = new EmailMessage();

        message.htmlBody = "<body>this email contains an inline image: <img src='cid:1234'>, html and test and an attachment<hr></body>";
        message.textBody = "test";
        message.recipient = "test@example.com";
        message.subject = "test subject";
        message.messageId = UUID.randomUUID().toString();

        final var iconAttachment = AttachmentSource.of(icon, "attachment.png", "image/png");
        final var iconCid = CidSource.of(icon, "1234", "image/png");
        m.marshal(config, message, List.of(iconAttachment), List.of(iconCid), Path.of("target/all.eml"));
    }

    @Test
    public void canMarshalTextAndAttachment() {

        final EmailSenderAndCopyAddresses config = new EmailSenderAndCopyAddresses();
        config.sender = "test.sender@example.com";
        config.senderDescription = "Test Sender";
        config.ccAddresses = List.of();
        config.bccAddresses = List.of();

        final EmailMessage email = new EmailMessage();

        email.htmlBody = null;
        email.textBody = "test";
        email.recipient = "test@example.com";
        email.subject = "test subject";
        email.messageId = UUID.randomUUID().toString();

        final var iconAttachment = AttachmentSource.of(icon, "attachment.png", "image/png");
        m.marshal(config, email, List.of(iconAttachment), List.of(), Path.of("target/text_and_attachment.eml"));
    }

    @Test
    public void canMarshalHtmlAndCids() {

        final EmailSenderAndCopyAddresses config = new EmailSenderAndCopyAddresses();
        config.sender = "test.sender@example.com";
        config.senderDescription = "Test Sender";
        config.ccAddresses = List.of();
        config.bccAddresses = List.of();

        final EmailMessage email = new EmailMessage();

        email.htmlBody = "<body><img src='cid:1234'><h1>test</h1> test test test<hr></body>";
        email.textBody = null;
        email.recipient = "test@example.com";
        email.subject = "test subject";
        email.messageId = UUID.randomUUID().toString();

        final var iconCid = CidSource.of(icon, "1234", "image/png");
        m.marshal(config, email, List.of(), List.of(iconCid), Path.of("target/html_and_cid.eml"));
    }

    @Test
    public void canMarshalHtmlOnly() {

        final EmailSenderAndCopyAddresses config = new EmailSenderAndCopyAddresses();
        config.sender = "test.sender@example.com";
        config.senderDescription = "Test Sender";
        config.ccAddresses = List.of();
        config.bccAddresses = List.of();

        final EmailMessage email = new EmailMessage();

        email.htmlBody = "<body>html only</body>";
        email.textBody = null;
        email.recipient = "test@example.com";
        email.subject = "test subject";
        email.messageId = UUID.randomUUID().toString();

        m.marshal(config, email, List.of(), List.of(), Path.of("target/html_only.eml"));
    }

    @Test
    public void canMarshalTextOnly() {

        final EmailSenderAndCopyAddresses config = new EmailSenderAndCopyAddresses();
        config.sender = "test.sender@example.com";
        config.senderDescription = "Test Sender";
        config.ccAddresses = List.of();
        config.bccAddresses = List.of();

        final EmailMessage email = new EmailMessage();

        email.htmlBody = null;
        email.textBody = "test";
        email.recipient = "test@example.com";
        email.subject = "test subject";
        email.messageId = UUID.randomUUID().toString();

        m.marshal(config, email, List.of(), List.of(), Path.of("target/text_only.eml"));
    }

}
