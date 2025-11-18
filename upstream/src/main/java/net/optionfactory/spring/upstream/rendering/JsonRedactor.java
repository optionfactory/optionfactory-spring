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

public class JsonRedactor {

    private final JsonMapper om;
    private final Map<JsonPointer, String> jsonPointers;

    public JsonRedactor(JsonMapper om, Map<JsonPointer, String> jsonPointers) {
        this.om = om;
        this.jsonPointers = jsonPointers;
    }

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
