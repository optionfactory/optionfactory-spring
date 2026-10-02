package net.optionfactory.spring.problems.web.l10n;

import java.util.Locale;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.StaticMessageSource;

public class MessageSourcesTest {

    private static final AggregateMessageSource CONTRIBUTED = new AggregateMessageSource("ContributorValidationMessages");

    private static StaticMessageSource source(String code, String message) {
        final var source = new StaticMessageSource();
        source.addMessage(code, Locale.ITALIAN, message);
        return source;
    }

    @Test
    public void theContributedBundleIsLocalized() {
        Assertions.assertEquals("Parametro mancante", CONTRIBUTED.getMessage("error.missing_parameter", null, Locale.ITALIAN), "the italian bundle must answer in italian");
    }

    @Test
    public void theContributedBundleIsReadAsUtf8() {
        Assertions.assertEquals("Parámetro faltante", CONTRIBUTED.getMessage("error.missing_parameter", null, Locale.of("es")), "non-ascii characters must be decoded as UTF-8");
    }

    @Test
    public void anUnknownCodeIsNotResolvedByTheContributedBundle() {
        Assertions.assertThrows(NoSuchMessageException.class, () -> CONTRIBUTED.getMessage("no.such.code", null, Locale.ITALIAN), "a code no bundle defines must not resolve");
    }

    @Test
    public void thePrimarySourceWins() {
        final var messages = new FallbackMessageSource(source("k", "primary"), source("k", "fallback"));
        Assertions.assertEquals("primary", messages.getMessage("k", null, Locale.ITALIAN), "a code both sources know must be resolved by the primary");
        Assertions.assertEquals("primary", messages.getMessage("k", null, "default", Locale.ITALIAN), "a code both sources know must be resolved by the primary, whatever the default");
    }

    @Test
    public void aCodeThePrimaryDoesNotKnowIsResolvedByTheFallback() {
        final var messages = new FallbackMessageSource(source("other", "primary"), source("k", "fallback"));
        Assertions.assertEquals("fallback", messages.getMessage("k", null, Locale.ITALIAN), "a code only the fallback knows must be resolved by it");
        Assertions.assertEquals("fallback", messages.getMessage(new DefaultMessageSourceResolvable("k"), Locale.ITALIAN), "a resolvable only the fallback knows must be resolved by it");
    }

    @Test
    public void aCodeNeitherKnowsYieldsTheDefaultMessageOrFails() {
        final var messages = new FallbackMessageSource(source("a", "primary"), source("b", "fallback"));
        Assertions.assertEquals("default", messages.getMessage("k", null, "default", Locale.ITALIAN), "the default message must be returned when neither source knows the code");
        Assertions.assertThrows(NoSuchMessageException.class, () -> messages.getMessage("k", null, Locale.ITALIAN), "without a default message, an unknown code must fail");
    }

    @Test
    public void aPrimaryUsingTheCodeAsDefaultHidesTheFallback() {
        final var primary = new StaticMessageSource();
        primary.setUseCodeAsDefaultMessage(true);
        final var messages = new FallbackMessageSource(primary, source("k", "fallback"));
        Assertions.assertEquals("k", messages.getMessage("k", null, Locale.ITALIAN), "a primary that knows every code never falls back");
    }
}
