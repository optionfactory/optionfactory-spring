package net.optionfactory.spring.upstream.mocks.rendering;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.util.ContentTypeUtils;

public class ThymeleafRenderer implements MocksRenderer {

    private final String[] templateSuffixes;
    private final SpringTemplateEngine engine;

    public ThymeleafRenderer(MessageSource messageSource, String[] templateSuffixes, IDialect[] dialects) {
        final var e = new SpringTemplateEngine();
        e.setTemplateResolver(new StringTemplateResolver());
        e.setTemplateEngineMessageSource(messageSource);
        for (IDialect dialect : dialects) {
            e.addDialect(dialect);
        }
        this.templateSuffixes = templateSuffixes;
        this.engine = e;
    }

    @Override
    public boolean canRender(Resource source) {
        final var filename = source.getFilename();
        return filename != null && Arrays.stream(templateSuffixes).anyMatch(suffix -> filename.endsWith(suffix));
    }

    @Override
    public Resource render(Resource source, InvocationContext invocation) {
        final var templateMode = ContentTypeUtils.computeTemplateModeForTemplateName(source.getFilename());
        try {
            final var sourceAsString = source.getContentAsString(StandardCharsets.UTF_8);
            final var spec = new TemplateSpec(sourceAsString, templateMode);
            final var out = engine.process(spec, invocation.expressions().thymeleafContext(invocation));
            return new ByteArrayResource(out.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
