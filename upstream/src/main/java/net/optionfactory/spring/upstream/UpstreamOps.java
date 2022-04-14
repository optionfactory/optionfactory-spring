package net.optionfactory.spring.upstream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;

public class UpstreamOps {

    private static final Set<String> LOGGED_MEDIA_TYPES = Set.of(
            "JSON",
            "TEXT",
            "XML",
            "HTML",
            "XHTML"
    );

    public static String bodyAsString(MediaType contentType, boolean logMultipart, InputStreamSource body) {
        if (contentType != null && contentType.isCompatibleWith(MediaType.MULTIPART_MIXED) && !logMultipart) {
            return "(multipart body)";
        }
        if (contentType != null && !LOGGED_MEDIA_TYPES.stream().anyMatch(t -> contentType.toString().toUpperCase().contains(t))) {
            return String.format("(binary:%s)", contentType);
        }
        try (var is = body.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return String.format("(binary:%s)", contentType);
        }
    }

}
