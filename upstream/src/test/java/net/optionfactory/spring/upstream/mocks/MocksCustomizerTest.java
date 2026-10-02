package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.optionfactory.spring.upstream.mocks.rendering.JsonTemplateRenderer;
import net.optionfactory.spring.upstream.mocks.rendering.MocksRenderer;
import net.optionfactory.spring.upstream.mocks.rendering.ThymeleafRenderer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

public class MocksCustomizerTest {

    private final AtomicReference<UpstreamHttpResponseFactory> responseFactory = new AtomicReference<>();
    private final List<MocksRenderer> renderers = new ArrayList<>();
    private final MocksCustomizer customizer = new MocksCustomizer(responseFactory, renderers);

    private ClientHttpResponse exchange() {
        return responseFactory.get().create(null, URI.create("http://example.com/"), HttpMethod.GET, new HttpHeaders());
    }

    private static String body(ClientHttpResponse response) throws IOException {
        return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    public void aFixedResponseAnswersEveryExchangeWithItsStatusAndUtf8Body() throws IOException {
        customizer.response(HttpStatus.ACCEPTED, MediaType.TEXT_PLAIN, "caffè");
        final var first = exchange();
        final var second = exchange();
        Assertions.assertEquals(HttpStatus.ACCEPTED, first.getStatusCode(), "the configured status must be returned");
        Assertions.assertEquals("Accepted", first.getStatusText(), "the status text must be the standard reason phrase");
        Assertions.assertEquals(MediaType.TEXT_PLAIN, first.getHeaders().getContentType(), "the configured media type must be the Content-Type");
        Assertions.assertEquals("caffè", body(first), "the body must be encoded as UTF-8");
        Assertions.assertEquals("caffè", body(second), "every exchange must get the full body again");
    }

    @Test
    public void theLastResponseConfiguredWins() throws IOException {
        customizer.response(MediaType.TEXT_PLAIN, "first").response(MediaType.TEXT_PLAIN, "second");
        Assertions.assertEquals("second", body(exchange()), "a later response must replace an earlier one");
    }

    @Test
    public void defaultsRegisterTheJsonTemplateRendererBeforeTheThymeleafOne() {
        customizer.defaults();
        Assertions.assertEquals(2, renderers.size(), "defaults must register two renderers");
        Assertions.assertInstanceOf(JsonTemplateRenderer.class, renderers.get(0), "the json template renderer must come first");
        Assertions.assertInstanceOf(ThymeleafRenderer.class, renderers.get(1), "the thymeleaf renderer must come second");
        Assertions.assertNull(responseFactory.get(), "registering renderers must not set a response factory");
    }

    @Test
    public void thymeleafRendersTheDefaultSuffixesOnly() {
        customizer.thymeleaf();
        final var renderer = renderers.get(0);
        for (String suffix : MocksCustomizer.DEFAULT_THYMELEAF_TEMPLATE_SUFFIXES) {
            Assertions.assertTrue(renderer.canRender(new ClassPathResource("mock" + suffix)), "a " + suffix + " resource must be rendered by thymeleaf");
        }
        Assertions.assertFalse(renderer.canRender(new ClassPathResource("mock.tpl.json")), "a json template must not be rendered by thymeleaf");
        Assertions.assertFalse(renderer.canRender(new ClassPathResource("mock.json")), "a plain resource must not be rendered by thymeleaf");
    }

    @Test
    public void jsontRendersTheTplJsonSuffix() {
        customizer.jsont();
        Assertions.assertTrue(renderers.get(0).canRender(new ClassPathResource("mock.tpl.json")), "a .tpl.json resource must be rendered as a json template");
        Assertions.assertFalse(renderers.get(0).canRender(new ClassPathResource("mock.json")), "a plain .json resource must not be rendered as a json template");
    }
}
