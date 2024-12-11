package net.optionfactory.spring.upstream.mocks.rendering;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.OverlayEvaluationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

public class JsonTemplateRenderer implements MocksRenderer {

    private final ObjectMapper om;
    private final String templateSuffix;

    public JsonTemplateRenderer(String templateSuffix, ObjectMapper om) {
        this.templateSuffix = templateSuffix;
        this.om = om;
    }

    @Override
    public boolean canRender(Resource source) {
        final var filename = source.getFilename();
        return filename != null && filename.endsWith(templateSuffix);
    }

    @Override
    public Resource render(Resource source, InvocationContext ctx) {
        final OverlayEvaluationContext oec = ctx.expressions().context(ctx);
        try (var is = source.getInputStream()) {
            final var input = om.readValue(is, JsonNode.class);
            final var output = process(om.getNodeFactory(), ctx.expressions(), oec, input);
            return new ByteArrayResource(om.writeValueAsBytes(output.node()));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static FragmentOrNode process(JsonNodeFactory jnf, Expressions expressions, OverlayEvaluationContext ctx, JsonNode input) {
        if (input.isArray()) {
            final var els = StreamSupport.stream(input.spliterator(), false)
                    .map(n -> process(jnf, expressions, ctx, n))
                    .filter(fn -> fn != null)
                    .flatMap(fn -> fn.nodes != null ? fn.nodes().stream() : Stream.of(fn.node()))
                    .filter(n -> n != null)
                    .toList();
            return FragmentOrNode.array(jnf, els);
        }
        if (input.isObject()) {
            final var fields = StreamSupport.stream(Spliterators.spliteratorUnknownSize(input.fieldNames(), 0), false)
                    .toList();

            final var firstField = fields.isEmpty() ? null : fields.get(0);
            if (firstField != null && firstField.startsWith("#if")) {
                final var condition = expressions.parse(input.get(firstField).textValue()).getValue(ctx, boolean.class);
                if (!condition) {
                    return null;
                }
                final var remainingFields = new LinkedHashMap<String, JsonNode>();
                for (String remainingProcField : fields.subList(1, fields.size())) {
                    remainingFields.put(remainingProcField, input.get(remainingProcField));
                }
                return FragmentOrNode.object(jnf, remainingFields);
            }
            if (firstField != null && firstField.startsWith("#each ")) {
                //needs to be expanded
                final var varName = firstField.substring("#each ".length());
                final var varValues = expressions.parse(input.get(firstField).textValue()).getValue(ctx, Iterable.class);
                final var otherFields = fields.subList(1, fields.size());
                final var fragmentEls = new ArrayList<JsonNode>();
                for (Object value : varValues) {
                    final var remainingFields = new LinkedHashMap<String, JsonNode>();
                    for (String remainingProcField : otherFields) {
                        remainingFields.put(remainingProcField, input.get(remainingProcField));
                    }
                    fragmentEls.add(process(jnf, expressions, ctx.createOverlay(varName, value), new ObjectNode(jnf, remainingFields)).node());
                }
                return FragmentOrNode.fragment(fragmentEls);
            }
            //already expanded
            final var children = new LinkedHashMap<String, JsonNode>();
            for (var field : fields) {
                final var k = expressions.parseTemplated(field).getValue(ctx, String.class);
                if (k == null) {
                    continue;
                }
                final var v = input.get(field);
                final var pv = process(jnf, expressions, ctx, v);
                children.put(k, pv.node());
            }
            return FragmentOrNode.object(jnf, children);

        }
        return FragmentOrNode.literal(input.isTextual() ? jnf.pojoNode(expressions.parseTemplated(input.asText()).getValue(ctx)) : input.deepCopy());
    }

    public record FragmentOrNode(JsonNode node, List<JsonNode> nodes) {

        public static FragmentOrNode array(JsonNodeFactory jnf, List<JsonNode> nodes) {
            return new FragmentOrNode(new ArrayNode(jnf, nodes), null);
        }

        public static FragmentOrNode object(JsonNodeFactory jnf, LinkedHashMap<String, JsonNode> nodes) {
            return new FragmentOrNode(new ObjectNode(jnf, nodes), null);
        }

        public static FragmentOrNode fragment(List<JsonNode> nodes) {
            return new FragmentOrNode(null, nodes);
        }

        public static FragmentOrNode literal(JsonNode node) {
            return new FragmentOrNode(node, null);
        }

    }

}
