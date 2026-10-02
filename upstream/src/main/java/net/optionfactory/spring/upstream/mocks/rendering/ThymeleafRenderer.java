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

/// Renders mock resources as thymeleaf templates.
///
/// The template mode follows the file extension (`.json` and `.js` javascript, `.xml` xml, `.html`
/// html, `.css` css, `.txt` text), so in a `.th.json` template `[[${...}]]` writes a json literal.
/// The template variables are `invocation`, `upstream`, `endpoint`, `args` and the method
/// parameters by name; when the client has an application context its beans are reachable as
/// `${@name}`.
///
/// ```json
/// {
///     "id": [[${id}]],
///     "endpoint": [[${endpoint}]]
/// }
/// ```
public class ThymeleafRenderer implements MocksRenderer {

    private final String[] templateSuffixes;
    private final SpringTemplateEngine engine;

    /// @param messageSource resolves the `#{...}` messages of the templates, or `null`
    /// @param templateSuffixes the file name suffixes of the resources to render
    /// @param dialects additional dialects, besides the spring standard one
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

    /// @param source the mock resource
    /// @return true when the resource has a file name ending with one of the template suffixes
    @Override
    public boolean canRender(Resource source) {
        final var filename = source.getFilename();
        return filename != null && Arrays.stream(templateSuffixes).anyMatch(suffix -> filename.endsWith(suffix));
    }

    /// @param source the template, read as UTF-8
    /// @param invocation the invocation the template is evaluated against
    /// @return the rendered template, encoded as UTF-8
    /// @throws java.io.UncheckedIOException when the template cannot be read
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
