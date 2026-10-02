package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/// Splits a multipart payload into its parts, so that each can be rendered on its own in logs.
///
/// A lenient parser meant for diagnostics: it does not validate the payload, and stops at the
/// first thing it cannot make sense of, returning the parts found until then.
public class MultipartParser {

    private static final byte[] HEADER_END_DELIMITER = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

    /// A part of a multipart payload.
    ///
    /// @param headers the part headers
    /// @param body the part body, as raw bytes
    public record ParsedPart(HttpHeaders headers, byte[] body) {

    }

    /// @param mediaType the media type, possibly `null`
    /// @return true for any `multipart/*` type, regardless of case
    public static boolean isMultipart(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        return mediaType.getType().equalsIgnoreCase("multipart");
    }

    /// Parses the parts delimited by the `boundary` parameter of the media type.
    ///
    /// Anything before the first delimiter is ignored, and parsing ends at the closing delimiter.
    /// Each part's headers, decoded as UTF-8, end at the first empty line, and its body at the next
    /// delimiter; a header line without a name or a `:` is skipped. A part without the empty line
    /// ending its headers ends the parsing, and a body without a following delimiter runs to the
    /// end of the payload.
    ///
    /// @param bodySource the multipart payload
    /// @param mediaType the payload media type, carrying the boundary
    /// @return the parts, in order; empty when no delimiter is found
    /// @throws IllegalArgumentException when the media type has no `boundary` parameter
    public static List<ParsedPart> parse(BodySource bodySource, MediaType mediaType) {
        final var boundary = mediaType.getParameter("boundary");
        if (boundary == null) {
            throw new IllegalArgumentException("Content-Type header is missing the 'boundary' parameter.");
        }
        final var parts = new ArrayList<ParsedPart>();
        final var delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        final var nextDelimiterWithCrlf = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);

        int index = 0;
        final var rawBodyBytes = bodySource.bytes();
        while (index < rawBodyBytes.length) {
            final int boundaryIndex = findSequence(rawBodyBytes, index, delimiter);
            if (boundaryIndex == -1) {
                break;
            }

            index = boundaryIndex + delimiter.length;

            if (index + 1 < rawBodyBytes.length && rawBodyBytes[index] == '-' && rawBodyBytes[index + 1] == '-') {
                break;
            }

            if (index + 1 < rawBodyBytes.length && rawBodyBytes[index] == '\r' && rawBodyBytes[index + 1] == '\n') {
                index += 2;
            }

            final int headerEndIndex = findSequence(rawBodyBytes, index, HEADER_END_DELIMITER);
            if (headerEndIndex == -1) {
                break;
            }

            final var headerBytes = Arrays.copyOfRange(rawBodyBytes, index, headerEndIndex);
            final var headerBlock = new String(headerBytes, StandardCharsets.UTF_8);
            final var headers = parseHeadersBlock(headerBlock);

            final int bodyStart = headerEndIndex + HEADER_END_DELIMITER.length;

            final int nextBoundaryIndex = findSequence(rawBodyBytes, bodyStart, nextDelimiterWithCrlf);
            final int bodyEnd = (nextBoundaryIndex != -1) ? nextBoundaryIndex : rawBodyBytes.length;

            final var bodyBytes = Arrays.copyOfRange(rawBodyBytes, bodyStart, bodyEnd);
            parts.add(new ParsedPart(headers, bodyBytes));

            index = bodyEnd + 2;
        }

        return parts;
    }

    private static int findSequence(byte[] source, int start, byte[] sequence) {
        for (int i = start; i != source.length; i++) {
            boolean match = true;
            for (int j = 0; j != sequence.length; j++) {
                if (i + j == source.length) {
                    return -1;
                }
                if (source[i + j] != sequence[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private static HttpHeaders parseHeadersBlock(@NonNull String headerBlock) {
        if (headerBlock.isBlank()) {
            return HttpHeaders.EMPTY;
        }
        final var headers = new HttpHeaders();
        headerBlock.lines().forEach(line -> {
            int colonIndex = line.indexOf(':');
            if (colonIndex <= 0) {
                return;
            }
            final var key = line.substring(0, colonIndex).trim();
            if (!key.isEmpty()) {
                final var value = line.substring(colonIndex + 1).trim();
                headers.add(key, value);
            }
        });
        return headers;
    }
}
