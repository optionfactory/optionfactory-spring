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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Serializes an [EmailMessage] to the MIME (`.eml`) format, the format the [EmailSender] reads
/// back from the spool.
///
/// The message is laid out as follows, with the parts a message does not have left out:
///
/// ```
/// multipart/mixed
/// ├── multipart/alternative
/// │   ├── text/plain             (textBody)
/// │   └── multipart/related
/// │       ├── text/html          (htmlBody)
/// │       └── one part per CidSource
/// └── one part per AttachmentSource
/// ```
///
/// Mail clients show the last alternative they support, so the html body is preferred when there is
/// one. The `Message-ID` header is the message's own [EmailMessage#messageId()], kept as is rather
/// than generated while serializing, so an `EmailMessage` keeps its id however many times it is
/// marshalled. Headers and bodies are encoded as UTF-8.
///
/// Instances are stateless and can be shared.
public class EmailMarshaller {

    private final Logger logger = LoggerFactory.getLogger(EmailMarshaller.class);

    /// Writes the message into the spool so that it is never seen half written: it is marshalled to
    /// a `.tmp` file, ignored by the [EmailSender], then renamed to `.eml` with an atomic move.
    /// When marshalling fails the temporary file is deleted.
    ///
    /// @param emailMessage the message to spool
    /// @param paths the spool to write into
    /// @param prefix the file name prefix, e.g. to tell the spooled emails apart, may be `null`
    /// @return the path of the spooled `.eml` file, named after `prefix` followed by a random part
    /// @throws EmailMarshallingException when the message cannot be marshalled
    /// @throws java.io.UncheckedIOException when the spool cannot be written to
    public Path marshalToSpool(EmailMessage emailMessage, EmailPaths paths, String prefix) {
        final Path tempPath;
        try {
            tempPath = Files.createTempFile(paths.spool(), prefix, ".tmp");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        try {
            marshal(emailMessage, tempPath);
            final Path targetPath = paths.spool().resolve(tempPath.getFileName().toString().replace(".tmp", ".eml"));
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
            return targetPath;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ex) {
                logger.warn(String.format("failed to delete temp file %s after marshal failure", tempPath), ex);
            }
        }
    }

    /// @param emailMessage the message to marshal
    /// @param path the file to write, created or overwritten
    /// @return `path`
    /// @throws EmailMarshallingException when the message cannot be marshalled or the file cannot
    /// be written
    public Path marshal(EmailMessage emailMessage, Path path) {
        try (final var os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            marshal(emailMessage, os);
            return path;
        } catch (IOException ex) {
            throw new EmailMarshallingException(ex.getMessage(), ex);
        }
    }

    /// @param emailMessage the message to marshal
    /// @return the marshalled message
    /// @throws EmailMarshallingException when the message cannot be marshalled
    public byte[] marshal(EmailMessage emailMessage) {
        try (final var baos = new ByteArrayOutputStream()) {
            marshal(emailMessage, baos);
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new EmailMarshallingException(ex.getMessage(), ex);
        }
    }

    /// Writes the message with the structure described in the class documentation.
    ///
    /// @param email the message to marshal
    /// @param os where the message is written, left open
    /// @throws EmailMarshallingException when the message cannot be marshalled, e.g. because an
    /// attachment cannot be read
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

    /// Thrown when a message cannot be built or serialized.
    public static class EmailMarshallingException extends IllegalStateException {

        /// @param message the detail message
        /// @param cause the underlying failure
        public EmailMarshallingException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    /// Adapts a spring `InputStreamSource` to the activation framework, so that attachments and
    /// inline resources are streamed from their source when the message is written instead of
    /// being loaded in memory beforehand.
    ///
    /// The data source is read-only.
    public static class InputStreamSourceDataSource implements DataSource {

        private final InputStreamSource iss;
        private final String contentType;

        /// @param iss provides the content, opened anew at each read
        /// @param contentType the content type of the data
        public InputStreamSourceDataSource(InputStreamSource iss, String contentType) {
            this.iss = iss;
            this.contentType = contentType;
        }

        /// @return a new stream from the source
        /// @throws IOException when the source cannot be opened
        @Override
        public InputStream getInputStream() throws IOException {
            return iss.getInputStream();
        }

        /// @return never
        /// @throws UnsupportedOperationException always, the data source is read-only
        @Override
        public OutputStream getOutputStream() throws IOException {
            throw new UnsupportedOperationException("cannot get an OutputStream from an InputStreamSourceDataSource");
        }

        /// @return the content type given at construction
        @Override
        public String getContentType() {
            return contentType;
        }

        /// @return an empty name: attachment file names are set on the MIME part instead
        @Override
        public String getName() {
            return "";
        }

    }

    /// A session-less `MimeMessage` whose `Message-ID` is fixed at construction.
    ///
    /// A plain `MimeMessage` replaces its `Message-ID` with a generated one whenever it is saved, so
    /// the id chosen by the application would be lost on the way to the spool.
    public static class PresetMessageIdMimeMessage extends MimeMessage {

        /// @param messageId the id, without angle brackets, which are added here
        /// @throws MessagingException when the header cannot be set
        public PresetMessageIdMimeMessage(String messageId) throws MessagingException {
            super((Session) null);
            setHeader("Message-ID", String.format("<%s>", messageId));
        }

        @Override
        protected void updateMessageID() throws MessagingException {
        }

    }
}
