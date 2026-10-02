package net.optionfactory.spring.thymeleaf;

import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IAttribute;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/// Busts browser caches across deployments by appending a `version` query parameter to the `src`
/// or `href` of the elements marked with `version:append` (or `data-version-append`).
///
/// ```java
/// engine.addDialect(new VersionedResourceDialect(() -> buildVersion));
/// ```
///
/// ```html
/// <script src="/static/app.js" version:append></script>
/// <!-- rendered as <script src="/static/app.js?version=1.2.3"></script> -->
/// ```
///
/// The marker attribute is always removed. The URL is left as it is when it is blank, or already
/// has a `version` parameter; an existing query is extended with `&version=`. The marker runs after
/// the standard dialect, so it versions the value a `th:src` or `th:href` evaluates to. Only HTML
/// template mode is processed.
public class VersionedResourceDialect extends AbstractProcessorDialect {
    private static final String DIALECT_NAME = "version";
    private static final String DIALECT_PREFIX = "version";
    private static final int DIALECT_PRECEDENCE = 1000;
    private final Supplier<String> versionSupplier;

    /// @param versionSupplier supplies the version, asked again on every processed element, so it
    /// can change while the application runs
    public VersionedResourceDialect(Supplier<String> versionSupplier) {
        super(DIALECT_NAME, DIALECT_PREFIX, DIALECT_PRECEDENCE);
        this.versionSupplier = versionSupplier;
    }

    /// @param dialectPrefix the prefix of the marker attribute, `version` unless configured otherwise
    /// on the engine
    /// @return the [AppendVersionToResource] processor
    @Override
    public Set<IProcessor> getProcessors(final String dialectPrefix) {
        return Collections.singleton(new AppendVersionToResource(dialectPrefix, versionSupplier));
    }

    /// Processes the `append` marker attribute on any element, appending the version to the first
    /// of its `src` and `href` attributes: when an element has both, only `src` is versioned.
    public static class AppendVersionToResource extends AbstractAttributeTagProcessor {

        private static final String ATTR_NAME = "append";
        private static final int PRECEDENCE = 10000;
        private static final List<String> attributeNames = Arrays.asList("src", "href");
        private final Supplier<String> versionSupplier;

        /// @param dialectPrefix the prefix of the marker attribute
        /// @param versionSupplier supplies the version, asked again on every processed element
        public AppendVersionToResource(
                final String dialectPrefix,
                final Supplier<String> versionSupplier
        ) {
            super(
                    TemplateMode.HTML,
                    dialectPrefix,
                    null,
                    false,
                    ATTR_NAME,
                    true,
                    PRECEDENCE,
                    true
            );
            this.versionSupplier = versionSupplier;
        }

        @Override
        protected void doProcess(
                ITemplateContext context,
                IProcessableElementTag tag,
                AttributeName attributeName,
                String attributeValue,
                IElementTagStructureHandler structureHandler
        ) {
            final Optional<IAttribute> maybeAttribute = attributeNames.stream()
                    .map(tag::getAttribute)
                    .filter(t -> t != null)
                    .findFirst();
            if (!maybeAttribute.isPresent()) {
                return;
            }
            final IAttribute attribute = maybeAttribute.get();

            final String content = attribute.getValue();
            if (content == null || content.isBlank()) {
                return;
            }

            final UriComponents uriComponents = UriComponentsBuilder.fromUriString(content).build();
            if (uriComponents.getQueryParams().containsKey("version")) {
                return;
            }

            final String versionedContent = UriComponentsBuilder.newInstance()
                    .uriComponents(uriComponents)
                    .queryParam("version", versionSupplier.get())
                    .build()
                    .toUriString();
            structureHandler.setAttribute(attribute.getAttributeCompleteName(), versionedContent, attribute.getValueQuotes());
        }

    }
}
