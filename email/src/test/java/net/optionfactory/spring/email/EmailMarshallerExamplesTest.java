package net.optionfactory.spring.email;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class EmailMarshallerExamplesTest {

    private final EmailMarshaller m = new EmailMarshaller();
    private final Resource icon = new ClassPathResource("icon.png", EmailMarshallerExamplesTest.class);

    @Test
    public void canMarshalCidsAttachmentsTextAndHtml() {

        final var em = EmailMessage.builder()
                .sender("test.sender@example.com", "Test sender")
                .recipient("test@example.com")
                .subject("test subject")
                .htmlBody("<body>this email contains an inline image: <img src='cid:1234'>, html and test and an attachment<hr></body>")
                .textBody("test")
                .attachments(AttachmentSource.of(icon, "attachment.png", "image/png"))
                .cids(CidSource.of(icon, "1234", "image/png"))
                .build();
        m.marshal(em, Path.of("target/all.eml"));
    }

    @Test
    public void canMarshalTextAndAttachment() {

        final var em = EmailMessage.builder()
                .sender("test.sender@example.com", "Test sender")
                .recipient("test@example.com")
                .subject("test subject")
                .textBody("test")
                .attachments(AttachmentSource.of(icon, "attachment.png", "image/png"))
                .build();

        m.marshal(em, Path.of("target/text_and_attachment.eml"));
    }

    @Test
    public void canMarshalHtmlAndCids() {

        final var em = EmailMessage.builder()
                .sender("test.sender@example.com", "Test sender")
                .recipient("test@example.com")
                .subject("test subject")
                .htmlBody("<body><img src='cid:1234'><h1>test</h1> test test test<hr></body>")
                .cids(CidSource.of(icon, "1234", "image/png"))
                .build();

        m.marshal(em, Path.of("target/html_and_cid.eml"));
    }

    @Test
    public void canMarshalHtmlOnly() {

        final var em = EmailMessage.builder()
                .sender("test.sender@example.com", "Test sender")
                .recipient("test@example.com")
                .subject("test subject")
                .htmlBody("<body>html only</body>")
                .build();

        m.marshal(em, Path.of("target/html_only.eml"));
    }

    @Test
    public void canMarshalTextOnly() {

        final var em = EmailMessage.builder()
                .sender("test.sender@example.com", "Test sender")
                .recipient("test@example.com")
                .subject("test subject")
                .textBody("text only")
                .build();

        m.marshal(em, Path.of("target/text_only.eml"));
    }

}
