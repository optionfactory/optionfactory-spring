package net.optionfactory.spring.upstream.mocks.rendering;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.util.ContentTypeUtils;

public class ThymeleafRenderer implements MocksRenderer {

    private final String templateSuffix;
    private final SpringTemplateEngine engine;
    private final ConfigurableApplicationContext ac;

    public ThymeleafRenderer(String templateSuffix, @Nullable ConfigurableApplicationContext ac, IDialect[] dialects) {
        final var e = new SpringTemplateEngine();
        e.setTemplateResolver(new StringTemplateResolver());
        for (IDialect dialect : dialects) {
            e.addDialect(dialect);
        }
        this.templateSuffix = templateSuffix;
        this.engine = e;
        this.ac = ac;
    }

    @Override
    public boolean canRender(Resource source) {
        final var filename = source.getFilename();
        return filename != null && filename.endsWith(templateSuffix);
    }

    @Override
    public Resource render(Resource source, InvocationContext invocation) {
        final var filename = source.getFilename();

        final var filenameWithoutSuffix = filename.substring(0, filename.lastIndexOf(templateSuffix));
        final var templateMode = ContentTypeUtils.computeTemplateModeForTemplateName(filenameWithoutSuffix);
        try {
            final var sourceAsString = source.getContentAsString(StandardCharsets.UTF_8);
            final var spec = new TemplateSpec(sourceAsString, templateMode);
            final var out = engine.process(spec, invocation.expressions().thymeleafContext(invocation, ac));
            return new ByteArrayResource(out.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
