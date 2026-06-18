package net.optionfactory.spring.problems.web.l10n;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import java.util.Locale;

public class FallbackMessageSource implements MessageSource {

    private final MessageSource primary;
    private final MessageSource fallback;

    public FallbackMessageSource(MessageSource primary, MessageSource fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        try {
            return primary.getMessage(code, args, locale);
        } catch (NoSuchMessageException ex) {
            return fallback.getMessage(code, args, defaultMessage, locale);
        }
    }

    @Override
    public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
        try {
            return primary.getMessage(code, args, locale);
        } catch (NoSuchMessageException ex) {
            return fallback.getMessage(code, args, locale);
        }
    }

    @Override
    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        try {
            return primary.getMessage(resolvable, locale);
        } catch (NoSuchMessageException ex) {
            return fallback.getMessage(resolvable, locale);
        }
    }
}
