package net.optionfactory.spring.upstream.mocks.rendering;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.util.ContentTypeUtils;

public class ThymeleafRenderer implements MocksRenderer {

    private final SpringTemplateEngine engine;

    public ThymeleafRenderer(@Nullable MessageSource ms, IDialect[] dialects) {
        final var e = new SpringTemplateEngine();
        e.setMessageSource(ms);
        e.setTemplateResolver(new StringTemplateResolver());
        for (IDialect dialect : dialects) {
            e.addDialect(dialect);
        }
        this.engine = e;
    }

    @Override
    public Resource render(Resource source, InvocationContext invocation) {
        final var filename = source.getFilename();
        if (filename == null || !filename.contains(".tpl.")) {
            return source;
        }
        final var context = new Context();
        context.setVariable("invocation", invocation);
        final var params = invocation.endpoint().method().getParameters();
        final var args = invocation.arguments();
        context.setVariable("args", args);
        for (int i = 0; i != params.length; ++i) {
            context.setVariable(params[i].getName(), args[i]);
        }
        final var templateMode = ContentTypeUtils.computeTemplateModeForTemplateName(filename);
        try {
            final var sourceAsString = source.getContentAsString(StandardCharsets.UTF_8);
            final var spec = new TemplateSpec(sourceAsString, templateMode);
            final var out = engine.process(spec, context);
            return new ByteArrayResource(out.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
