package net.optionfactory.spring.email;

import org.springframework.core.io.InputStreamSource;

/// A file attached to an [EmailMessage], shown by mail clients as a downloadable attachment.
///
/// The content is read only when the message is marshalled, and it can be read more than once, so
/// the source must provide a fresh stream each time it is asked (a spring `Resource` does; an
/// `InputStreamResource` wrapping an already opened stream does not). A source that fails to open
/// fails the marshalling with an [EmailMarshaller.EmailMarshallingException].
///
/// @param source provides the attachment content
/// @param fileName the file name proposed to the recipient
/// @param mimeType the content type of the attachment, e.g. `application/pdf`
public record AttachmentSource(InputStreamSource source, String fileName, String mimeType) {

    /// @param source provides the attachment content
    /// @param fileName the file name proposed to the recipient
    /// @param mimeType the content type of the attachment
    /// @return the attachment source
    public static AttachmentSource of(InputStreamSource source, String fileName, String mimeType) {
        return new AttachmentSource(source, fileName, mimeType);
    }

}
