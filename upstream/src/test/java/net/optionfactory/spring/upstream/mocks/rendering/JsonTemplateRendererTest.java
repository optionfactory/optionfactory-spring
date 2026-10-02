package net.optionfactory.spring.upstream.mocks.rendering;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class JsonTemplateRendererTest {

    public interface Endpoint {

        void call(String name, List<String> items);
    }

    private static final JsonMapper MAPPER = new JsonMapper();
    private final JsonTemplateRenderer renderer = new JsonTemplateRenderer(".tpl.json", MAPPER);

    private static InvocationContext invocation(Object... args) throws NoSuchMethodException {
        final var method = Endpoint.class.getMethod("call", String.class, List.class);
        final var expressions = new Expressions(null, Map.of("fromBuilder", "builder-var"));
        return new InvocationContext(expressions, null, null, new EndpointDescriptor("up", "ep", method, null), args, "boot", 1, null, Buffering.BUFFERED);
    }

    private JsonNode render(String template, Object... args) throws IOException, NoSuchMethodException {
        final var rendered = renderer.render(new ByteArrayResource(template.getBytes(StandardCharsets.UTF_8)), invocation(args));
        return MAPPER.readTree(rendered.getContentAsByteArray());
    }

    private static JsonNode json(String json) {
        return MAPPER.readTree(json);
    }

    @Test
    public void canRenderMockResourcesWithJsonTemplate() {

        final var client = UpstreamBuilder.create(JsonTemplateClient.class)
                .requestFactoryMock(c -> c.jsont())
                .json(JsonMapper.builder().build())
                .baseUri("http://example.com")
                .build();

        Map<String, String> got = client.testEndpoint("passed");

        Assertions.assertEquals(Map.of("key", "passed"), got, "the template must be evaluated against the invocation arguments");
    }

    @Test
    public void aSingleExpressionKeepsTheTypeOfItsValue() throws Exception {
        final var got = render("""
                {"number": "#{1+1}", "bool": "#{true}", "mixed": "n#{1+1}", "literal": 3, "nil": null}
                """, "x", List.of());
        Assertions.assertEquals(json("""
                {"number": 2, "bool": true, "mixed": "n2", "literal": 3, "nil": null}
                """), got, "a lone expression must keep its type, a mixed template must become a string, literals must be copied");
    }

    @Test
    public void templatesSeeTheInvocationAndTheBuilderVariables() throws Exception {
        final var got = render("""
                {"name": "#{#name}", "where": "#{#upstream}/#{#endpoint}", "var": "#{#fromBuilder}"}
                """, "x", List.of());
        Assertions.assertEquals(json("""
                {"name": "x", "where": "up/ep", "var": "builder-var"}
                """), got, "templates must see the parameters by name, the upstream and endpoint names and the builder variables");
    }

    @Test
    public void keysAreTemplatesAndANullKeyDropsTheEntry() throws Exception {
        final var got = render("""
                {"#{#name}": 1, "#{null}": 2}
                """, "x", List.of());
        Assertions.assertEquals(json("""
                {"x": 1}
                """), got, "keys must be evaluated, and an entry with a null key dropped");
    }

    @Test
    public void eachExpandsAnArrayElementPerItem() throws Exception {
        final var got = render("""
                [{"#each item": "#items", "value": "#{#item}", "name": "#{#name}"}, "tail"]
                """, "x", List.of("a", "b"));
        Assertions.assertEquals(json("""
                [{"value": "a", "name": "x"}, {"value": "b", "name": "x"}, "tail"]
                """), got, "#each must produce one element per item, with the item bound and the outer variables still visible");
    }

    @Test
    public void ifDropsAnArrayElementWhenFalse() throws Exception {
        final var got = render("""
                [{"#if": "#name == 'y'", "k": 1}, {"#if": "#name == 'x'", "k": 2}, 3]
                """, "x", List.of());
        Assertions.assertEquals(json("""
                [{"k": 2}, 3]
                """), got, "#if must drop the element when false and keep the remaining fields when true");
    }

    @Test
    public void aTrueIfEvaluatesTheRemainingFields() throws Exception {
        final var got = render("""
                [{"#if": "#name == 'x'", "name": "#{#name}", "#{#name}": "#{1+1}"}]
                """, "x", List.of());
        Assertions.assertEquals(json("""
                [{"name": "x", "x": 2}]
                """), got, "the fields kept by a true #if must be evaluated");
    }

    @Test
    public void aFalseIfAtTheRootRendersNull() throws Exception {
        final var got = render("""
                {"#if": "#name == 'y'", "k": 1}
                """, "x", List.of());
        Assertions.assertTrue(got.isNull(), "a false #if at the root must render a json null");
    }

    @Test
    public void aFalseIfAsAFieldValueDropsTheField() throws Exception {
        final var got = render("""
                {"a": {"#if": "#name == 'y'", "k": 1}, "b": {"#if": "#name == 'x'", "k": 2}}
                """, "x", List.of());
        Assertions.assertEquals(json("""
                {"b": {"k": 2}}
                """), got, "a false #if as a field value must drop the field, a true one must keep the object");
    }

    @Test
    public void eachAsAFieldValueRendersAnArray() throws Exception {
        final var got = render("""
                {"items": {"#each item": "#items", "value": "#{#item}"}}
                """, "x", List.of("a", "b"));
        Assertions.assertEquals(json("""
                {"items": [{"value": "a"}, {"value": "b"}]}
                """), got, "#each as a field value must render an array of one object per item");
    }

    @Test
    public void anIfAfterAnEachFiltersTheItems() throws Exception {
        final var got = render("""
                [{"#each item": "#items", "#if": "#item != 'b'", "value": "#{#item}"}]
                """, "x", List.of("a", "b", "c"));
        Assertions.assertEquals(json("""
                [{"value": "a"}, {"value": "c"}]
                """), got, "an #if following an #each must drop the items it is false for");
    }

    @Test
    public void rendersOnlyResourcesWithTheTemplateSuffix() {
        Assertions.assertTrue(renderer.canRender(new ClassPathResource("mock.tpl.json")), "a resource with the template suffix must be rendered");
        Assertions.assertFalse(renderer.canRender(new ClassPathResource("mock.json")), "a resource without the template suffix must not be rendered");
        Assertions.assertFalse(renderer.canRender(new ByteArrayResource(new byte[0])), "a resource without a file name must not be rendered");
    }
}
