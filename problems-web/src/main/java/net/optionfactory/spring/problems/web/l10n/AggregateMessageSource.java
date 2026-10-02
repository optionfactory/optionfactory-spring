package net.optionfactory.spring.problems.web.l10n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import org.hibernate.validator.resourceloading.PlatformResourceBundleLocator;
import org.springframework.context.support.AbstractMessageSource;

/// A message source reading a resource bundle merged from every copy of it on the classpath.
///
/// Spring's `ResourceBundleMessageSource` reads the first bundle of a given name it finds. This one
/// uses hibernate validator's aggregating bundle locator instead, so that several jars can each
/// contribute messages to the same bundle: this is how the `ContributorValidationMessages` of
/// `problems-web`, `validators` and the application are all found. Bundles are read as UTF-8 and
/// messages formatted with `MessageFormat`.
public class AggregateMessageSource extends AbstractMessageSource {

    private final PlatformResourceBundleLocator locator;

    /// @param bundleName the base name of the bundles to merge, e.g. `ContributorValidationMessages`
    public AggregateMessageSource(String bundleName) {
        this.locator = new PlatformResourceBundleLocator(bundleName, null, true);
    }

    @Override
    protected MessageFormat resolveCode(String code, Locale locale) {
        ResourceBundle bundle = locator.getResourceBundle(locale);
        if (bundle != null && bundle.containsKey(code)) {
            return createMessageFormat(bundle.getString(code), locale);
        }
        return null;
    }
}