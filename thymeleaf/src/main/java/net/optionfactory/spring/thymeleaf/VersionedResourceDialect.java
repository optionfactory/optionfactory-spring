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

public class VersionedResourceDialect extends AbstractProcessorDialect {
    private static final String DIALECT_NAME = "version";
    private static final String DIALECT_PREFIX = "version";
    private static final int DIALECT_PRECEDENCE = 1000;
    private final Supplier<String> versionSupplier;

    public VersionedResourceDialect(Supplier<String> versionSupplier) {
        super(DIALECT_NAME, DIALECT_PREFIX, DIALECT_PRECEDENCE);
        this.versionSupplier = versionSupplier;
    }

    @Override
    public Set<IProcessor> getProcessors(final String dialectPrefix) {
        return Collections.singleton(new AppendVersionToResource(dialectPrefix, versionSupplier));
    }

    public static class AppendVersionToResource extends AbstractAttributeTagProcessor {

        private static final String ATTR_NAME = "append";
        private static final int PRECEDENCE = 10000;
        private static final List<String> attributeNames = Arrays.asList("src", "href");
        private final Supplier<String> versionSupplier;

        public AppendVersionToResource(
                final String dialectPrefix,
                final Supplier<String> versionSupplier
        ) {
            super(
                    TemplateMode.HTML,
                    dialectPrefix,
                    null, // apply to all tags
                    false, // no prefix to be applied to tag name
                    ATTR_NAME,
                    true, // apply dialect prefix to attribute name
                    PRECEDENCE,
                    true // remove matched attribute afterwards
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
