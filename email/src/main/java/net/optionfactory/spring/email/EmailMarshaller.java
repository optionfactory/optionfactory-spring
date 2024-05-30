package net.optionfactory.spring.email;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.springframework.core.io.InputStreamSource;

public class EmailMarshaller {

    public Path marshalToSpool(EmailMessage emailMessage, EmailPaths paths, String prefix) {
        try {
            final Path tempPath = Files.createTempFile(paths.spool(), prefix, ".tmp");
            marshal(emailMessage, tempPath);
            final Path targetPath = paths.spool().resolve(tempPath.getFileName().toString().replace(".tmp", ".eml"));
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
            return targetPath;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public Path marshal(EmailMessage emailMessage, Path path) {
        try (final var os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            marshal(emailMessage, os);
            return path;
        } catch (IOException ex) {
            throw new EmailMarshallingException(ex.getMessage(), ex);
        }
    }

    public byte[] marshal(EmailMessage emailMessage) {
        try (final var baos = new ByteArrayOutputStream()) {
            marshal(emailMessage, baos);
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
     * @param email the email message to be marshalled
     * @param os the outputStream
     */
    public void marshal(EmailMessage email, OutputStream os) {
        try {
            final var message = new PresetMessageIdMimeMessage(email.messageId());
            message.setSubject(email.subject(), "UTF-8");
            message.setFrom(email.sender());
            message.setRecipients(Message.RecipientType.TO, email.recipients());
            message.setRecipients(Message.RecipientType.CC, email.ccAddresses());
            message.setRecipients(Message.RecipientType.BCC, email.bccAddresses());
            message.setReplyTo(email.replyTo());
            final var alternatives = new MimeMultipart("alternative");

            if (email.textBody() != null) {
                final var textContent = new MimeBodyPart();
                textContent.setText(email.textBody(), "UTF-8");
                alternatives.addBodyPart(textContent);
            }
            if (email.htmlBody() != null) {
                final var related = new MimeMultipart("related");

                final var htmlContent = new MimeBodyPart();
                htmlContent.setContent(email.htmlBody(), "text/html; charset=utf-8");

                related.addBodyPart(htmlContent);
                for (CidSource cs : email.cids()) {
                    final var source = new InputStreamSourceDataSource(cs.source(), cs.mimeType());
                    final var mbp = new MimeBodyPart();
                    mbp.setDataHandler(new DataHandler(source));
                    mbp.setContentID(cs.id());
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
            for (AttachmentSource as : email.attachments()) {
                final var source = new InputStreamSourceDataSource(as.source(), as.mimeType());
                final var mbp = new MimeBodyPart();
                mbp.setDataHandler(new DataHandler(source));
                mbp.setFileName(as.fileName());
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
            throw new UnsupportedOperationException("cannot get an OutputStream from an InputStreamSourceDataSource");
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
