package net.optionfactory.spring.upstream.rendering;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.springframework.core.io.InputStreamSource;
import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/// Redacts the values a set of json pointers address in a json body before it is logged.
///
/// Each addressed value, whatever its type (an object or array included), is replaced by the
/// configured string; a pointer that addresses nothing is ignored. The result is compact json.
///
/// A pointer segment holding an escaped `~0` or `~1` is used still escaped: `/a~1b` does not
/// redact the field `a/b`, and adds a field named `a~1b` instead.
public class JsonRedactor {

    private final JsonMapper om;
    private final Map<JsonPointer, String> jsonPointers;

    /// @param om the mapper reading the body
    /// @param jsonPointers the replacement value of each pointer to redact
    public JsonRedactor(JsonMapper om, Map<JsonPointer, String> jsonPointers) {
        this.om = om;
        this.jsonPointers = jsonPointers;
    }

    /// @param source the json body
    /// @return the redacted body, as compact json
    /// @throws java.io.UncheckedIOException when the body cannot be read
    /// @throws tools.jackson.core.JacksonException when the body is not json
    public String redact(InputStreamSource source) {
        try (final var is = source.getInputStream()) {
            final var root = om.readValue(is, JsonNode.class);
            for (var ptrAndValue : jsonPointers.entrySet()) {
                final var ptr = ptrAndValue.getKey();
                final var match = root.at(ptr);
                if (match.isMissingNode()) {
                    continue;
                }
                final var parent = root.at(ptr.head());
                if (parent.isMissingNode()) {
                    continue;
                }
                if (parent.isObject()) {
                    final var fieldName = ptr.last().toString().substring(1);
                    ((ObjectNode) parent).put(fieldName, ptrAndValue.getValue());
                }
                if (parent.isArray()) {
                    final var index = Integer.parseInt(ptr.last().toString().substring(1));
                    ((ArrayNode) parent).set(index, ptrAndValue.getValue());
                }
            }
            return root.toString();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
