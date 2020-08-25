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
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailSenderAndCopyAddresses;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.Result;
import org.springframework.core.io.InputStreamSource;
import org.springframework.util.StreamUtils;

public class EmailMarshaller {

    public Result<Path> marshal(EmailSenderAndCopyAddresses messageConfiguration, EmailMessage emailMessage, List<MimeBodyPart> attachments, List<MimeBodyPart> cids, Path path) {
        try (final var os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            Result<Void> r = marshal(messageConfiguration, emailMessage, attachments, cids, os);
            if (r.isError()) {
                return r.mapErrors();
            }
            return Result.value(path);
        } catch (IOException ex) {
            return Result.error(Problem.of("MESSAGING_EXCEPTION", null, null, ex.getMessage()));
        }
    }

    public Result<byte[]> marshal(EmailSenderAndCopyAddresses addresses, EmailMessage emailMessage, List<MimeBodyPart> attachments, List<MimeBodyPart> cids) {
        try (final var baos = new ByteArrayOutputStream()) {
            final Result<Void> r = marshal(addresses, emailMessage, attachments, cids, baos);
            if (r.isError()) {
                return r.mapErrors();
            }
            return Result.value(baos.toByteArray());
        } catch (IOException ex) {
            return Result.error(Problem.of("MESSAGING_EXCEPTION", null, null, ex.getMessage()));
        }
    }

    /**
     * Message is created and serialized with this structure:
     * <code>
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
     */
    public Result<Void> marshal(EmailSenderAndCopyAddresses addresses, EmailMessage emailMessage, List<MimeBodyPart> attachments, List<MimeBodyPart> cids, OutputStream os) {
        try {
            final var message = new PresetMessageIdMimeMessage(emailMessage.messageId);
            message.setHeader("X-OF-Message-Id", emailMessage.messageId);
            message.setSubject(emailMessage.subject, "UTF-8");
            final InternetAddress senderInternetAddress = new InternetAddress(addresses.sender, addresses.senderDescription, "UTF-8");
            message.setFrom(senderInternetAddress);
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailMessage.recipient, false));
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(addresses.ccAddresses.stream().collect(Collectors.joining(",")), false));
            message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(addresses.bccAddresses.stream().collect(Collectors.joining(",")), false));
            message.setReplyTo(new InternetAddress[]{senderInternetAddress});
            final Multipart alternatives = new MimeMultipart("alternative");

            if (emailMessage.textBody != null) {
                final MimeBodyPart textContent = new MimeBodyPart();
                textContent.setText(emailMessage.textBody, "utf-8");
                alternatives.addBodyPart(textContent);
            }
            if (emailMessage.htmlBody != null) {
                final Multipart related = new MimeMultipart("related");
                
                final MimeBodyPart htmlContent = new MimeBodyPart();
                htmlContent.setContent(emailMessage.htmlBody, "text/html; charset=utf-8");
                
                related.addBodyPart(htmlContent);
                for (MimeBodyPart cid : cids) {
                    related.addBodyPart(cid);
                }
                
                final MimeBodyPart relatedAsBodyPart = new MimeBodyPart();
                relatedAsBodyPart.setContent(related);
                
                alternatives.addBodyPart(relatedAsBodyPart);
            }
            final MimeBodyPart alternativesAsPart = new MimeBodyPart();
            alternativesAsPart.setContent(alternatives);
            final Multipart textsAndAttachments = new MimeMultipart("mixed");
            textsAndAttachments.addBodyPart(alternativesAsPart);
            for (MimeBodyPart attachment : attachments) {
                textsAndAttachments.addBodyPart(attachment);
            }
            message.setContent(textsAndAttachments);
            message.writeTo(os);
            return Result.value(null);
        } catch (IOException | MessagingException ex) {
            return Result.error(Problem.of("MESSAGING_EXCEPTION", null, null, ex.getMessage()));
        }
    }

    public Result<MimeBodyPart> attachment(InputStreamSource local, String fileName, String mimeType) {
        try (final InputStream is = local.getInputStream()) {
            final var attachment = new MimeBodyPart();
            final var source = new ByteArrayDataSource(StreamUtils.copyToByteArray(is), mimeType);
            attachment.setDataHandler(new DataHandler(source));
            attachment.setFileName(fileName);
            return Result.value(attachment);
        } catch (IOException | MessagingException ex) {
            return Result.error(Problem.of("MESSAGING_EXCEPTION", null, null, ex.getMessage()));
        }
    }
    
    public Result<MimeBodyPart> cid(InputStreamSource local, String cid, String mimeType) {
        try (final InputStream is = local.getInputStream()) {
            final var attachment = new MimeBodyPart();
            final var source = new ByteArrayDataSource(StreamUtils.copyToByteArray(is), mimeType);
            attachment.setDataHandler(new DataHandler(source));
            attachment.setContentID(cid);
            return Result.value(attachment);
        } catch (IOException | MessagingException ex) {
            return Result.error(Problem.of("MESSAGING_EXCEPTION", null, null, ex.getMessage()));
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
