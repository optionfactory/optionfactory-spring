package net.optionfactory.spring.upstream.mocks.rendering;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.thymeleaf.dialect.IDialect;
import tools.jackson.databind.json.JsonMapper;

public class ThymeleafRendererTest {

    public interface Endpoint {

        void call(String name);
    }

    private static InvocationContext invocation(Object... args) throws NoSuchMethodException {
        final var method = Endpoint.class.getMethod("call", String.class);
        return new InvocationContext(new Expressions(null, null), null, null, new EndpointDescriptor("up", "ep", method, null), args, "boot", 1, null, Buffering.BUFFERED);
    }

    private static Resource template(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private static ThymeleafRenderer renderer(MessageSource messageSource) {
        return new ThymeleafRenderer(messageSource, new String[]{".th.txt", ".th.json"}, new IDialect[0]);
    }

    @Test
    public void canRenderMockResourcesWithThymeleaf() {

        final var client = UpstreamBuilder.create(ThymeleafClient.class)
                .requestFactoryMock(c -> c.thymeleaf())
                .json(JsonMapper.builder().build())
                .baseUri("http://example.com")
                .build();

        Map<String, String> got = client.testEndpoint("passed");

        Assertions.assertEquals(Map.of("key", "passed"), got, "the template must be evaluated against the invocation arguments");
    }

    @Test
    public void templatesSeeTheInvocationVariables() throws Exception {
        final var rendered = renderer(null).render(template("mock.th.txt", "[[${upstream}]]/[[${endpoint}]]/[[${name}]]"), invocation("x"));
        Assertions.assertEquals("up/ep/x", rendered.getContentAsString(StandardCharsets.UTF_8), "templates must see the upstream and endpoint names and the parameters by name");
    }

    @Test
    public void jsonTemplatesWriteJsonLiterals() throws Exception {
        final var rendered = renderer(null).render(template("mock.th.json", "{\"k\": [[${name}]]}"), invocation("x"));
        Assertions.assertEquals("{\"k\": \"x\"}", rendered.getContentAsString(StandardCharsets.UTF_8), "a .json template must be rendered in javascript mode, quoting strings");
    }

    @Test
    public void messagesAreResolvedThroughTheMessageSource() throws IOException, NoSuchMethodException {
        final var messages = new StaticMessageSource();
        messages.addMessage("greeting", Locale.getDefault(), "hello");
        final var rendered = renderer(messages).render(template("mock.th.txt", "[[#{greeting}]]"), invocation("x"));
        Assertions.assertEquals("hello", rendered.getContentAsString(StandardCharsets.UTF_8), "#{...} must be resolved through the configured message source");
    }

    @Test
    public void rendersOnlyResourcesWithOneOfTheSuffixes() {
        final var renderer = renderer(null);
        Assertions.assertTrue(renderer.canRender(new ClassPathResource("mock.th.txt")), "a resource with a configured suffix must be rendered");
        Assertions.assertFalse(renderer.canRender(new ClassPathResource("mock.th.xml")), "a resource with a suffix that is not configured must not be rendered");
        Assertions.assertFalse(renderer.canRender(new ByteArrayResource(new byte[0])), "a resource without a file name must not be rendered");
    }
}
