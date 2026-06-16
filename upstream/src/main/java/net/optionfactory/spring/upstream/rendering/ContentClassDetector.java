package net.optionfactory.spring.upstream.rendering;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;


public final class ContentClassDetector {

    public enum ContentClass {
        TEXT,
        BINARY
    }

    public static ContentClass detect(@Nullable MediaType mediaType, @NonNull byte[] content) {
        if (content.length == 0) {
            return ContentClass.TEXT;
        }
        if (mediaType != null && isDefinitelyText(mediaType)) {
            return ContentClass.TEXT;
        }
        return isTextHeuristic(content) ? ContentClass.TEXT : ContentClass.BINARY;
    }

    private static boolean isDefinitelyText(MediaType mediaType) {
        final String type = mediaType.getType();
        final String subtype = mediaType.getSubtype();

        return "text".equals(type)
                || "json".equals(subtype)
                || "xml".equals(subtype)
                || subtype.endsWith("+json")
                || subtype.endsWith("+xml")
                || "javascript".equals(subtype)
                || "x-www-form-urlencoded".equals(subtype);
    }

    private static boolean isTextHeuristic(byte[] content) {
        final int scanLimit = Math.min(content.length, 1024);
        for (int i = 0; i < scanLimit; i++) {
            final int b = content[i] & 0xFF;

            if (b == 0x00) {
                return false;
            }
            // tab lf and cr are ok
            if (b < 32 && b != 9 && b != 10 && b != 13) {
                return false;
            }
        }
        return true;
    }
}
