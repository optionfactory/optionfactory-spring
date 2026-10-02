package net.optionfactory.spring.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

public class ThymeleafToPdfRendererTest {

    private ThymeleafToPdfRenderer renderer;

    @BeforeEach
    public void setup() throws Exception {
        final var resolver = new ClassLoaderTemplateResolver();
        resolver.setOrder(1);
        resolver.setResolvablePatterns(Set.of("*.html"));
        resolver.setPrefix("/example/");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("utf-8");
        resolver.setCacheable(true);

        final var templateEngine = new SpringTemplateEngine();
        templateEngine.addTemplateResolver(resolver);

        this.renderer = new ThymeleafToPdfRenderer(templateEngine, List.of(
                PdfFontInfo.of("font_opensans.ttf", "OpenSans", 400, BaseRendererBuilder.FontStyle.NORMAL, true),
                PdfFontInfo.of("font_opensans_bold.ttf", "OpenSans", 700, BaseRendererBuilder.FontStyle.NORMAL, true)
        ), Optional.of("producer"));
    }

    @Test
    public void canRender() throws Exception {
        Resource rendered = renderer.render("example.html", new Context());
        dump(rendered, "target/example.rendered.pdf");
        try (final var doc = Loader.loadPDF(new File("target/example.rendered.pdf"))) {
            final var got = new PDFTextStripper().getText(doc);
            Assertions.assertEquals("test", got.trim());
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
        dump(renderer.renderXhtml(xhtml), "target/example.xhtml.pdf");
        try (final var doc = Loader.loadPDF(new File("target/example.xhtml.pdf"))) {
            final var got = new PDFTextStripper().getText(doc);
            Assertions.assertEquals("[[${1 + 1}]] kept", got.trim(), "the xhtml is rendered as it is, its text not evaluated");
        }
    }

    public void dump(Resource in, String out) {
        try (var fos = new FileOutputStream(out); var is = in.getInputStream()) {
            StreamUtils.copy(is, fos);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

    }
}
