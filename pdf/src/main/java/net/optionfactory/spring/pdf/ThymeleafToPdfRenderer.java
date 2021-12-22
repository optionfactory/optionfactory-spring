package net.optionfactory.spring.pdf;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.extend.impl.FSDefaultCacheStore;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.StreamUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

public class ThymeleafToPdfRenderer {

    private final TemplateEngine templateEngine;
    private final byte[] colorProfile;
    private final List<PdfFontInfo> fonts;
    private final FSDefaultCacheStore cache;

    public ThymeleafToPdfRenderer(TemplateEngine templateEngine, List<PdfFontInfo> fonts) {
        try (final InputStream is = ThymeleafToPdfRenderer.class.getResourceAsStream("sRGB.icc")) {
            this.colorProfile = StreamUtils.copyToByteArray(is);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        this.fonts = fonts;
        this.templateEngine = templateEngine;
        this.cache = new FSDefaultCacheStore();
        XRLog.setLoggingEnabled(false);
    }

    public Resource render(String template, Context context) {
        final var xhtml = templateEngine.process(template, context);
        try {
            final var nonSigned = new FastByteArrayOutputStream(64 * 1024);
            final var builder = new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(xhtml, "")
                    .toStream(nonSigned)
                    .usePdfVersion(1.7f)
                    .usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_3_A)
                    .useCacheStore(PdfRendererBuilder.CacheStore.PDF_FONT_METRICS, cache)
                    .useColorProfile(colorProfile);
            
            fonts.forEach(font -> {
                builder.useFont(new ClassPathFontSupplier(font.path), font.family, font.weight, font.style, font.subset);
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
