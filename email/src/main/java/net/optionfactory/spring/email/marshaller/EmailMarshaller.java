package net.optionfactory.spring.email.marshaller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailSenderAndCopyAddresses;
import org.springframework.core.io.InputStreamSource;

public class EmailMarshaller {

    public Path marshal(EmailSenderAndCopyAddresses messageConfiguration, EmailMessage emailMessage, List<AttachmentSource> attachments, List<CidSource> cids, Path path) {
        try (final var os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            marshal(messageConfiguration, emailMessage, attachments, cids, os);
            return path;
        } catch (IOException ex) {
            throw new EmailMarshallingException(ex.getMessage(), ex);
        }
    }

    public byte[] marshal(EmailSenderAndCopyAddresses addresses, EmailMessage emailMessage, List<AttachmentSource> attachments, List<CidSource> cids) {
        try (final var baos = new ByteArrayOutputStream()) {
            marshal(addresses, emailMessage, attachments, cids, baos);
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new EmailMarshallingException(ex.getMessage(), ex);
        }
    }

    /**
     * Message is created and serialized with this structure:      <code>
     *   * multipart/mixed
     *     * multipart/alternative
     *        * textBody
     *        * multipart/related
     *          * htmlBody
     *          * CID inline image
     *          * CID inline image
     *     * attachment
     *     * attachment
     * </code>
     *
     * @param addresses recipient, ccs, bccs
     * @param emailMessage the email message to be marshalled
     * @param attachments resources to be attached
     * @param cids resources inlined in the html part
     * @param os the outputStream
     */
    public void marshal(EmailSenderAndCopyAddresses addresses, EmailMessage emailMessage, List<AttachmentSource> attachments, List<CidSource> cids, OutputStream os) {
        try {
            final var message = new PresetMessageIdMimeMessage(emailMessage.messageId);
            message.setSubject(emailMessage.subject, "UTF-8");
            final InternetAddress senderInternetAddress = new InternetAddress(addresses.sender, addresses.senderDescription, "UTF-8");
            message.setFrom(senderInternetAddress);
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailMessage.recipient, false));
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(addresses.ccAddresses.stream().collect(Collectors.joining(",")), false));
            message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(addresses.bccAddresses.stream().collect(Collectors.joining(",")), false));
            message.setReplyTo(new InternetAddress[]{senderInternetAddress});
            final var alternatives = new MimeMultipart("alternative");

            if (emailMessage.textBody != null) {
                final var textContent = new MimeBodyPart();
                textContent.setText(emailMessage.textBody, "UTF-8");
                alternatives.addBodyPart(textContent);
            }
            if (emailMessage.htmlBody != null) {
                final var related = new MimeMultipart("related");

                final var htmlContent = new MimeBodyPart();
                htmlContent.setContent(emailMessage.htmlBody, "text/html; charset=utf-8");

                related.addBodyPart(htmlContent);
                for (CidSource cs : cids) {
                    final var source = new InputStreamSourceDataSource(cs.source, cs.mimeType);
                    final var mbp = new MimeBodyPart();
                    mbp.setDataHandler(new DataHandler(source));
                    mbp.setContentID(cs.id);
                    related.addBodyPart(mbp);
                }

                final var relatedAsBodyPart = new MimeBodyPart();
                relatedAsBodyPart.setContent(related);

                alternatives.addBodyPart(relatedAsBodyPart);
            }
            final var alternativesAsPart = new MimeBodyPart();
            alternativesAsPart.setContent(alternatives);
            final var textsAndAttachments = new MimeMultipart("mixed");
            textsAndAttachments.addBodyPart(alternativesAsPart);
            for (AttachmentSource as : attachments) {
                final var source = new InputStreamSourceDataSource(as.source, as.mimeType);
                final var mbp = new MimeBodyPart();
                mbp.setDataHandler(new DataHandler(source));
                mbp.setFileName(as.fileName);
                textsAndAttachments.addBodyPart(mbp);
            }
            message.setContent(textsAndAttachments);
            message.writeTo(os);
        } catch (IOException | MessagingException ex) {
            throw new EmailMarshallingException(ex.getMessage(), ex);
        }
    }

    public static class EmailMarshallingException extends IllegalStateException {

        public EmailMarshallingException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public static class AttachmentSource {

        public InputStreamSource source;
        public String fileName;
        public String mimeType;

        public static AttachmentSource of(InputStreamSource source, String fileName, String mimeType) {
            final var as = new AttachmentSource();
            as.source = source;
            as.fileName = fileName;
            as.mimeType = mimeType;
            return as;
        }

    }

    public static class CidSource {

        public InputStreamSource source;
        public String id;
        public String mimeType;

        public static CidSource of(InputStreamSource source, String id, String mimeType) {
            final var cs = new CidSource();
            cs.source = source;
            cs.id = id;
            cs.mimeType = mimeType;
            return cs;
        }
    }

    public static class InputStreamSourceDataSource implements DataSource {

        private final InputStreamSource iss;
        private final String contentType;

        public InputStreamSourceDataSource(InputStreamSource iss, String contentType) {
            this.iss = iss;
            this.contentType = contentType;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return iss.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            throw new UnsupportedOperationException("cannot get an OutputStrewam from an InputStreamSourceDataSource");
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getName() {
            return "";
        }

    }

    public static class PresetMessageIdMimeMessage extends MimeMessage {

        public PresetMessageIdMimeMessage(String messageId) throws MessagingException {
            super((Session) null);
            setHeader("Message-ID", String.format("<%s>", messageId));
        }

        @Override
        protected void updateMessageID() throws MessagingException {
            //we don't want it to be updated
        }

    }
}
