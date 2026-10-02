package net.optionfactory.spring.problems.web.l10n;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import java.util.Locale;

/// Resolves messages from a primary source, falling back to another for the codes the primary does
/// not know.
///
/// A code is unknown when the primary throws `NoSuchMessageException`: a primary configured to use
/// the code as the default message never does, and so never falls back.
public class FallbackMessageSource implements MessageSource {

    private final MessageSource primary;
    private final MessageSource fallback;

    /// @param primary the source consulted first
    /// @param fallback the source consulted for the codes the primary does not know
    public FallbackMessageSource(MessageSource primary, MessageSource fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    /// @param code the message code
    /// @param args the message arguments, or `null`
    /// @param defaultMessage returned when neither source knows the code, as the fallback returns it
    /// @param locale the locale
    /// @return the primary's message, else the fallback's, else `defaultMessage`
    @Override
    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        try {
            return primary.getMessage(code, args, locale);
        } catch (NoSuchMessageException ex) {
            return fallback.getMessage(code, args, defaultMessage, locale);
        }
    }

    /// @param code the message code
    /// @param args the message arguments, or `null`
    /// @param locale the locale
    /// @return the primary's message, else the fallback's
    /// @throws NoSuchMessageException when neither source knows the code
    @Override
    public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
        try {
            return primary.getMessage(code, args, locale);
        } catch (NoSuchMessageException ex) {
            return fallback.getMessage(code, args, locale);
        }
    }

    /// @param resolvable the codes, arguments and default message to resolve
    /// @param locale the locale
    /// @return the primary's message, else the fallback's
    /// @throws NoSuchMessageException when neither source can resolve it
    @Override
    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        try {
            return primary.getMessage(resolvable, locale);
        } catch (NoSuchMessageException ex) {
            return fallback.getMessage(resolvable, locale);
        }
    }
}
