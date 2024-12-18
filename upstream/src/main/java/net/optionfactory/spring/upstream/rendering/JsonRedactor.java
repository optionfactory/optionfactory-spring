package net.optionfactory.spring.upstream.rendering;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.InputStreamSource;

public class JsonRedactor {

    private final ObjectMapper om;
    private final List<JsonPointer> jsonPointers;

    public JsonRedactor(ObjectMapper om, List<JsonPointer> jsonPointers) {
        this.om = om;
        this.jsonPointers = jsonPointers;
    }

    public String redact(InputStreamSource source) {
        try (final var is = source.getInputStream()) {
            final var root = om.readValue(is, JsonNode.class);
            for (var ptr : jsonPointers) {
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
                    ((ObjectNode) parent).put(fieldName, "<redacted>");
                }
                if (parent.isArray()) {
                    final var index = Integer.parseInt(ptr.last().toString().substring(1));
                    ((ArrayNode) parent).set(index, "<redacted>");
                }
            }
            return root.toString();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
