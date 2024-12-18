package net.optionfactory.spring.upstream.rendering;

import com.fasterxml.jackson.core.JsonPointer;
import java.util.List;
import java.util.Map;

public class RedactConfigurer {

    private final Map<String, String> namespaces;
    private final List<String> tags;
    private final List<String> attributes;
    private final List<JsonPointer> jsonPtrs;

    public RedactConfigurer(Map<String, String> namespaces, List<String> tags, List<String> attributes, List<JsonPointer> jsonPtrs) {
        this.namespaces = namespaces;
        this.tags = tags;
        this.attributes = attributes;
        this.jsonPtrs = jsonPtrs;
    }

    public RedactConfigurer namespace(String prefix, String uri) {
        this.namespaces.put(prefix, uri);
        return this;
    }

    public RedactConfigurer tag(String tag) {
        this.tags.add(tag);
        return this;
    }

    public RedactConfigurer attr(String attribute) {
        this.attributes.add(attribute);
        return this;
    }

    public RedactConfigurer jsonPtr(String expression) {
        this.jsonPtrs.add(JsonPointer.valueOf(expression));
        return this;
    }

}
