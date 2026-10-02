package net.optionfactory.spring.upstream.mocks.rendering;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.OverlayEvaluationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.expression.EvaluationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/// Renders mock resources that are json documents with SpEL templates in them.
///
/// The template is parsed as json and rebuilt node by node:
///
/// - every string is a SpEL template (`#{...}`), evaluated with `#upstream`, `#endpoint`,
///   `#invocation`, `#args`, the method parameters by name, the builder's expression variables
///   and, when the client has an application context, its beans as `@name`. A string made of a
///   single `#{...}` keeps the type of its value (`"#{1+1}"` becomes the number `2`), any other
///   string becomes a string;
/// - object keys are templates as well, and an entry whose key evaluates to `null` is dropped;
/// - numbers, booleans and `null` are copied as they are.
///
/// Two directives, recognized as the first field of an object that is an array element, shape
/// arrays:
///
/// - `"#if": "<expression>"` keeps the element only when the SpEL expression (not a template) is
///   true. The kept element is made of the remaining fields as they are written: their templates
///   are not evaluated;
/// - `"#each <name>": "<expression>"` replaces the element with one object per item of the
///   `Iterable` the expression yields, each made of the remaining fields evaluated with the item
///   bound to `#<name>`.
///
/// A directive found outside an array (in the root object, or in an object that is the value of a
/// field) fails the rendering with a `NullPointerException` when it removes the object (`#if`)
/// or when the output is written (`#each`).
///
/// ```json
/// {
///     "id": "#{#id}",
///     "items": [
///         {"#each item": "#items", "name": "#{#item.name()}"},
///         {"#if": "#admin", "name": "root"}
///     ]
/// }
/// ```
public class JsonTemplateRenderer implements MocksRenderer {

    private final String templateSuffix;
    private final JsonMapper om;

    /// @param templateSuffix the file name suffix of the resources to render
    /// @param om the mapper parsing the template and writing the result
    public JsonTemplateRenderer(String templateSuffix, JsonMapper om) {
        this.templateSuffix = templateSuffix;
        this.om = om;
    }

    /// @param source the mock resource
    /// @return true when the resource has a file name ending with the template suffix
    @Override
    public boolean canRender(Resource source) {
        final var filename = source.getFilename();
        return filename != null && filename.endsWith(templateSuffix);
    }

    /// @param source the template
    /// @param ctx the invocation the template is evaluated against
    /// @return the rendered json
    /// @throws java.io.UncheckedIOException when the template cannot be read or parsed
    /// @throws EvaluationException when an expression cannot be evaluated
    @Override
    public Resource render(Resource source, InvocationContext ctx) {
        final OverlayEvaluationContext oec = ctx.expressions().context(ctx);
        try (var is = source.getInputStream()) {
            final var input = om.readValue(is, JsonNode.class);
            final var output = process(input, ctx.expressions(), oec, om.getNodeFactory());
            return new ByteArrayResource(om.writeValueAsBytes(output.node()));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static FragmentOrNode process(JsonNode input, Expressions expressions, OverlayEvaluationContext ctx, JsonNodeFactory jnf) {
        if (input.isArray()) {
            return array(input, expressions, ctx, jnf);
        }
        if (input.isObject()) {
            return object(input, expressions, ctx, jnf);
        }
        return FragmentOrNode.literal(input.isString()
                ? jnf.pojoNode(expressions.parseTemplated(input.asString()).getValue(ctx))
                : input.deepCopy()
        );
    }

    private static FragmentOrNode object(JsonNode input, Expressions expressions, OverlayEvaluationContext ctx, JsonNodeFactory jnf) throws EvaluationException {
        final var fields = input.propertyNames().stream().toList();
        
        final var firstField = fields.isEmpty() ? null : fields.get(0);
        if (firstField != null && firstField.startsWith("#if")) {
            final var condition = expressions.parse(input.get(firstField).stringValue()).getValue(ctx, boolean.class);
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
            final var varName = firstField.substring("#each ".length());
            final var varValues = expressions.parse(input.get(firstField).stringValue()).getValue(ctx, Iterable.class);
            final var otherFields = fields.subList(1, fields.size());
            final var fragmentEls = new ArrayList<JsonNode>();
            for (var value : varValues) {
                final var remainingFields = new LinkedHashMap<String, JsonNode>();
                for (String remainingProcField : otherFields) {
                    remainingFields.put(remainingProcField, input.get(remainingProcField));
                }
                fragmentEls.add(process(new ObjectNode(jnf, remainingFields), expressions, ctx.createOverlay(varName, value), jnf).node());
            }
            return FragmentOrNode.fragment(fragmentEls);
        }
        final var children = new LinkedHashMap<String, JsonNode>();
        for (var field : fields) {
            final var k = expressions.parseTemplated(field).getValue(ctx, String.class);
            if (k == null) {
                continue;
            }
            final var v = input.get(field);
            final var pv = process(v, expressions, ctx, jnf);
            children.put(k, pv.node());
        }
        return FragmentOrNode.object(jnf, children);
    }

    private static FragmentOrNode array(JsonNode input, Expressions expressions, OverlayEvaluationContext ctx, JsonNodeFactory jnf) {
        final var els = StreamSupport.stream(input.spliterator(), false)
                .map(n -> process(n, expressions, ctx, jnf))
                .filter(fn -> fn != null)
                .flatMap(fn -> fn.nodes != null ? fn.nodes().stream() : Stream.of(fn.node()))
                .filter(n -> n != null)
                .toList();
        return FragmentOrNode.array(jnf, els);
    }
    
    /// The result of rendering a template node: a single `node`, or the `nodes` of a fragment that
    /// an `#each` directive splices into the enclosing array. Exactly one of the two is not `null`.
    ///
    /// @param node the rendered node, `null` for a fragment
    /// @param nodes the nodes of a fragment, `null` for a single node
    public record FragmentOrNode(JsonNode node, List<JsonNode> nodes) {

        /// @param jnf the node factory
        /// @param nodes the array elements
        /// @return a single array node
        public static FragmentOrNode array(JsonNodeFactory jnf, List<JsonNode> nodes) {
            return new FragmentOrNode(new ArrayNode(jnf, nodes), null);
        }

        /// @param jnf the node factory
        /// @param nodes the object fields, in order
        /// @return a single object node
        public static FragmentOrNode object(JsonNodeFactory jnf, LinkedHashMap<String, JsonNode> nodes) {
            return new FragmentOrNode(new ObjectNode(jnf, nodes), null);
        }

        /// @param nodes the nodes to splice into the enclosing array
        /// @return a fragment
        public static FragmentOrNode fragment(List<JsonNode> nodes) {
            return new FragmentOrNode(null, nodes);
        }

        /// @param node the node
        /// @return a single node
        public static FragmentOrNode literal(JsonNode node) {
            return new FragmentOrNode(node, null);
        }

    }

}
