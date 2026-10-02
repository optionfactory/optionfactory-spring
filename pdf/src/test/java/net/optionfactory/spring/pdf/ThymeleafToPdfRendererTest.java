package net.optionfactory.spring.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

public class ThymeleafToPdfRendererTest {

    private static final List<PdfFontInfo> FONTS = List.of(
            PdfFontInfo.of("font_opensans.ttf", "OpenSans", 400, BaseRendererBuilder.FontStyle.NORMAL, true),
            PdfFontInfo.of("font_opensans_bold.ttf", "OpenSans", 700, BaseRendererBuilder.FontStyle.NORMAL, true)
    );

    private static SpringTemplateEngine templateEngine;
    private static ThymeleafToPdfRenderer renderer;

    @BeforeAll
    public static void setup() throws Exception {
        final var resolver = new ClassLoaderTemplateResolver();
        resolver.setOrder(1);
        resolver.setResolvablePatterns(Set.of("*.html"));
        resolver.setPrefix("/example/");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("utf-8");
        resolver.setCacheable(true);

        templateEngine = new SpringTemplateEngine();
        templateEngine.addTemplateResolver(resolver);

        renderer = new ThymeleafToPdfRenderer(templateEngine, FONTS, Optional.of("producer"));
    }

    private static PDDocument load(Resource pdf) throws IOException {
        return Loader.loadPDF(pdf.getContentAsByteArray());
    }

    @Test
    public void canRender() throws Exception {
        try (final var doc = load(renderer.render("example.html", new Context()))) {
            final var got = new PDFTextStripper().getText(doc);
            Assertions.assertEquals("test", got.trim(), "the template text is rendered");
        }
    }

    @Test
    public void renderEvaluatesTheTemplateWithTheContext() throws Exception {
        final var context = new Context();
        context.setVariable("name", "world");
        try (final var doc = load(renderer.render("greeting.html", context))) {
            final var got = new PDFTextStripper().getText(doc);
            Assertions.assertEquals("hello world", got.trim(), "the context variables are evaluated before rendering");
        }
    }

    @Test
    public void canRenderXhtmlWithoutEvaluatingIt() throws Exception {
        final var xhtml = """
                <html xmlns="http://www.w3.org/1999/xhtml">
                    <head><style>body { font-family: OpenSans; }</style></head>
                    <body>[[${1 + 1}]] <span th:text="'replaced'" xmlns:th="http://www.thymeleaf.org">kept</span></body>
                </html>
                """;
        try (final var doc = load(renderer.renderXhtml(xhtml))) {
            final var got = new PDFTextStripper().getText(doc);
            Assertions.assertEquals("[[${1 + 1}]] kept", got.trim(), "the xhtml is rendered as it is, its text not evaluated");
        }
    }

    @Test
    public void documentsArePdfA3aWithTheConfiguredProducer() throws Exception {
        try (final var doc = load(renderer.render("example.html", new Context()))) {
            Assertions.assertEquals(1.7f, doc.getVersion(), "documents are PDF 1.7");
            Assertions.assertEquals("producer", doc.getDocumentInformation().getProducer(), "the configured producer is written in the document information");
            final var xmp = new String(doc.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            Assertions.assertTrue(xmp.contains("<pdfaid:part>3</pdfaid:part>") && xmp.contains("<pdfaid:conformance>A</pdfaid:conformance>"), "the XMP metadata declares PDF/A-3a conformance");
            Assertions.assertTrue(doc.getDocumentCatalog().getMarkInfo().isMarked(), "documents are tagged, as PDF/UA requires");
            Assertions.assertFalse(doc.getDocumentCatalog().getOutputIntents().isEmpty(), "the sRGB output intent is embedded");
        }
    }

    @Test
    public void withoutProducerTheProducerIsEmpty() throws Exception {
        final var anonymous = new ThymeleafToPdfRenderer(templateEngine, FONTS, Optional.empty());
        try (final var doc = load(anonymous.render("example.html", new Context()))) {
            Assertions.assertEquals("", doc.getDocumentInformation().getProducer(), "no producer means an empty one, not the openhtmltopdf default");
        }
    }
}
