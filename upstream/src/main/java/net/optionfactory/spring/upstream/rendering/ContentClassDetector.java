package net.optionfactory.spring.upstream.rendering;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;


/// Tells text payloads, which can be rendered in logs, from binary ones, which are only described.
public final class ContentClassDetector {

    /// The class of a payload.
    public enum ContentClass {
        /// Renderable as text.
        TEXT,
        /// To be described rather than rendered.
        BINARY
    }

    /// Classifies a payload by its media type when that is conclusive, by its content otherwise.
    ///
    /// The media types taken as text are `text/*`, `json`, `xml`, `+json` and `+xml` subtypes,
    /// `javascript` and `x-www-form-urlencoded`. Any other payload is text unless one of its first
    /// 1024 bytes is a control character other than tab, line feed and carriage return; bytes
    /// beyond those are not inspected, and non-ASCII bytes do not make a payload binary.
    ///
    /// @param mediaType the declared media type, or `null` when unknown
    /// @param content the payload
    /// @return [ContentClass#TEXT] for an empty payload, a text media type or text-looking content,
    /// [ContentClass#BINARY] otherwise
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
            if (b < 32 && b != 9 && b != 10 && b != 13) {
                return false;
            }
        }
        return true;
    }
}
