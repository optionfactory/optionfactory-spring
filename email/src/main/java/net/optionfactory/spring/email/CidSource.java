package net.optionfactory.spring.email;

import org.springframework.core.io.InputStreamSource;

public record CidSource(InputStreamSource source, String id, String mimeType) {

    public static CidSource of(InputStreamSource source, String id, String mimeType) {
        return new CidSource(source, id, mimeType);
    }

}
