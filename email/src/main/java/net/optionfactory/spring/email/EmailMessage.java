package net.optionfactory.spring.email;

import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;
import net.optionfactory.spring.email.marshaller.EmailMarshaller.EmailMarshallingException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

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
        private String subject;
        private String textBody;
        private String htmlBody;
        private List<AttachmentSource> attachments;
        private List<CidSource> cids;

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

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder textBody(String textBody) {
            this.textBody = textBody;
            return this;
        }

        public Builder htmlBody(String htmlBody) {
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder cids(List<CidSource> cids) {
            this.cids = cids;
            return this;
        }

        public Builder cids(CidSource... cids) {
            this.cids = List.of(cids);
            return this;
        }

        public Builder attachments(List<AttachmentSource> attachments) {
            this.attachments = attachments;
            return this;
        }

        public Builder attachments(AttachmentSource... attachments) {
            this.attachments = List.of(attachments);
            return this;
        }
        
        public Prototype prototype(){
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
            builder.subject = subject;
            builder.textBody = textBody;
            builder.htmlBody = htmlBody;
            builder.attachments = attachments;
            builder.cids = cids;
            return builder;
        }

        public EmailMessage build() {
            Assert.notNull(sender, "sender must be configured");
            Assert.notNull(subject, "subject must be configured");
            Assert.notNull(recipients, "recipients must be configured");
            Assert.isTrue(textBody != null || htmlBody != null, "at least one of textBody,htmlBody must be configured");
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
