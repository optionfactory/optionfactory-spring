package net.optionfactory.spring.email;

import org.springframework.core.io.InputStreamSource;

public record AttachmentSource(InputStreamSource source, String fileName, String mimeType) {

    public static AttachmentSource of(InputStreamSource source, String fileName, String mimeType) {
        return new AttachmentSource(source, fileName, mimeType);
    }

}
