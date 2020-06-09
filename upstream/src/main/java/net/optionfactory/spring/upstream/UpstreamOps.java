package net.optionfactory.spring.upstream;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.UriComponentsBuilder;

public class UpstreamOps {

    private static final Pattern UUID_WITH_ONLY_LETTERS = Pattern.compile("[A-Fa-f]{8}-[A-Fa-f]{4}-[A-Fa-f]{4}-[A-Fa-f]{4}-[A-Fa-f]{12}");
    private static final Pattern CONTAINS_NUMBERS = Pattern.compile("[0-9]");

    public static String guessEndpointNameFromUri(URI uri) {
        final Predicate<String> probablyAName = (what) -> !CONTAINS_NUMBERS.matcher(what).matches() && (what.length() != 36 || !UUID_WITH_ONLY_LETTERS.matcher(what).matches());
        return UriComponentsBuilder.fromUri(uri).build().getPathSegments().stream().filter(probablyAName).reduce("/", (f, s) -> s);
    }

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
