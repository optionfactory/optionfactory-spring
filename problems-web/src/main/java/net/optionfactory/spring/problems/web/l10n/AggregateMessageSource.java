package net.optionfactory.spring.problems.web.l10n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import org.hibernate.validator.resourceloading.PlatformResourceBundleLocator;
import org.springframework.context.support.AbstractMessageSource;

public class AggregateMessageSource extends AbstractMessageSource {

    private final PlatformResourceBundleLocator locator;

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