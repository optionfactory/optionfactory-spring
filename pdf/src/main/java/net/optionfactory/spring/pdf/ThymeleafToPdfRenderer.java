package net.optionfactory.spring.pdf;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.extend.impl.FSDefaultCacheStore;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.StreamUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/// Renders Thymeleaf templates, or XHTML produced elsewhere, to PDF/A-3a documents with
/// openhtmltopdf.
///
/// Every document is a tagged PDF 1.7 declaring PDF/A-3a and PDF/UA conformance, with an
/// embedded sRGB color profile. The markup is laid out by openhtmltopdf, so it must be well-formed
/// XHTML styled with the CSS openhtmltopdf supports; since PDF/A requires embedded fonts, the text
/// should use only the font families of the configured [PdfFontInfo]s.
///
/// A renderer is meant to be built once and shared: every render uses its own builder and
/// document, while the font metrics are cached across renders in a concurrent map. It is therefore
/// safe for concurrent use as long as the template engine is. Building a renderer disables
/// openhtmltopdf logging for the whole JVM.
///
/// ```java
/// final var renderer = new ThymeleafToPdfRenderer(templateEngine, List.of(
///         PdfFontInfo.of("font_opensans.ttf", "OpenSans", 400, FontStyle.NORMAL, true),
///         PdfFontInfo.of("font_opensans_bold.ttf", "OpenSans", 700, FontStyle.NORMAL, true)
/// ), Optional.of("my-app"));
/// final Resource pdf = renderer.render("invoice", context);
/// ```
public class ThymeleafToPdfRenderer {

    private final TemplateEngine templateEngine;
    private final byte[] colorProfile;
    private final List<PdfFontInfo> fonts;
    private final FSDefaultCacheStore cache;
    private final String producer;


    /// @param templateEngine the engine processing the templates given to
    /// [#render(String, Context)]
    /// @param fonts the fonts made available to the documents
    /// @param producer the `Producer` written in the document information; when empty the producer
    /// is blank, rather than openhtmltopdf's own
    public ThymeleafToPdfRenderer(TemplateEngine templateEngine, List<PdfFontInfo> fonts, Optional<String> producer) {
        try (final InputStream is = ThymeleafToPdfRenderer.class.getResourceAsStream("sRGB.icc")) {
            this.colorProfile = StreamUtils.copyToByteArray(is);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        this.fonts = fonts;
        this.templateEngine = templateEngine;
        this.cache = new FSDefaultCacheStore();
        this.producer = producer.orElse("");
        XRLog.setLoggingEnabled(false);
    }

    /// Processes a template and renders the resulting markup as [#renderXhtml(String)] does.
    ///
    /// @param template the template name, as the template engine resolves it
    /// @param context the template variables and locale
    /// @return the PDF, in memory
    /// @throws org.thymeleaf.exceptions.TemplateEngineException when the template cannot be
    /// resolved or processed
    /// @throws java.io.UncheckedIOException when the rendering fails on I/O
    public Resource render(String template, Context context) {
        return renderXhtml(templateEngine.process(template, context));
    }

    /// Renders an XHTML document produced elsewhere, e.g. by an XSL transformation, without going
    /// through Thymeleaf: its text is never evaluated as template expressions.
    ///
    /// @param xhtml the document
    /// @return the PDF, in memory
    /// @throws java.io.UncheckedIOException when the rendering fails on I/O
    public Resource renderXhtml(String xhtml) {
        try(final var doc = new PDDocument()){
            final var nonSigned = new FastByteArrayOutputStream(64 * 1024);
            final var builder = new PdfRendererBuilder()
                    .usePdfVersion(1.7f)
                    .usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_3_A)
                    .usePdfUaAccessibility(true)
                    .useCacheStore(PdfRendererBuilder.CacheStore.PDF_FONT_METRICS, cache)
                    .useColorProfile(colorProfile)
                    .withHtmlContent(xhtml, "")
                    .withProducer(producer)
                    .usePDDocument(doc)
                    .toStream(nonSigned);

            fonts.forEach(font -> {
                builder.useFont(new ClassPathFontSupplier(font.path()), font.family(), font.weight(), font.style(), font.subset());
            });
            try (final var renderer = builder.buildPdfRenderer()) {
                renderer.layout();
                renderer.createPDF();
            }
            return new ByteArrayResource(nonSigned.toByteArrayUnsafe());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static class ClassPathFontSupplier implements FSSupplier<InputStream> {

        private final String path;

        public ClassPathFontSupplier(String path) {
            this.path = path;
        }

        @Override
        public InputStream supply() {
            return ClassPathFontSupplier.class.getResourceAsStream(path);
        }

    }

}
