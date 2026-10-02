package net.optionfactory.spring.localizedenums.dialects;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import net.optionfactory.spring.localizedenums.AnEnum;
import net.optionfactory.spring.localizedenums.AnEnumWithBodies;
import net.optionfactory.spring.localizedenums.LocalizedEnumResponse;
import net.optionfactory.spring.localizedenums.ResourceBundleEnumsLocalizationService;
import net.optionfactory.spring.localizedenums.ResourceBundleEnumsLocalizationService.ResolutionMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

public class LocalizedEnumsTest {

    @BeforeEach
    public void setUp() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    public void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static LocalizedEnums dialect(ResolutionMode mode) {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        return new LocalizedEnums(new ResourceBundleEnumsLocalizationService("enums", source, AnEnum.class, mode));
    }

    @Test
    public void valueTranslatesInTheLocaleOfTheCurrentThread() {
        final var source = new ResourceBundleMessageSource();
        source.setBasenames("localization-dialect");
        source.setFallbackToSystemLocale(false);
        final var d = new LocalizedEnums(new ResourceBundleEnumsLocalizationService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NAME));
        LocaleContextHolder.setLocale(Locale.ITALIAN);
        Assertions.assertEquals("Tradotto", d.value("AnEnum", "VALUE_1"), "the locale held by LocaleContextHolder selects the bundle");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        Assertions.assertEquals("Translated", d.value("AnEnum", "VALUE_1"), "a locale without its own bundle falls back to the base one");
    }

    @Test
    public void valueTranslatesInTheCurrentLocale() {
        Assertions.assertEquals("Translated", dialect(ResolutionMode.MISSING_AS_NAME).value("AnEnum", "VALUE_1"), "the translation of the current locale");
    }

    @Test
    public void valueOfAMissingTranslationIsTheNameWhenMissingAsName() {
        Assertions.assertEquals("VALUE_2", dialect(ResolutionMode.MISSING_AS_NAME).value("AnEnum", "VALUE_2"), "a missing translation resolves to the name");
    }

    @Test
    public void valueOfAMissingTranslationThrowsWhenMissingAsNull() {
        Assertions.assertThrows(NoSuchElementException.class, () -> dialect(ResolutionMode.MISSING_AS_NULL).value("AnEnum", "VALUE_2"), "a missing translation has no value to answer");
    }

    @Test
    public void valuesOfACategoryListItsConstantsInDeclarationOrder() {
        Assertions.assertEquals(List.of(
                LocalizedEnumResponse.of("AnEnum", "VALUE_1", "Translated"),
                LocalizedEnumResponse.of("AnEnum", "VALUE_2", "VALUE_2")
        ), dialect(ResolutionMode.MISSING_AS_NAME).values("AnEnum"), "every constant of the category, in declaration order");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void valuesOfAnEnumClassListItsConstants() {
        final var enumClass = (Class<Enum<?>>) (Class<?>) AnEnum.class;
        Assertions.assertEquals(2, dialect(ResolutionMode.MISSING_AS_NAME).values(enumClass).size(), "one entry per constant of the enum");
    }

    @Test
    public void inMatchesByName() {
        final var d = dialect(ResolutionMode.MISSING_AS_NAME);
        final var value1 = LocalizedEnumResponse.of("AnEnum", "VALUE_1", "Translated");
        Assertions.assertTrue(d.in(value1, List.of(AnEnum.VALUE_1)), "the collection holds the constant");
        Assertions.assertFalse(d.in(value1, List.of(AnEnum.VALUE_2)), "the collection holds another constant only");
    }

    @Test
    public void inIgnoresTheCategory() {
        final var d = dialect(ResolutionMode.MISSING_AS_NAME);
        final var plain = LocalizedEnumResponse.of("AnEnum", "PLAIN", "Plain");
        Assertions.assertTrue(d.in(plain, List.of(AnEnumWithBodies.PLAIN)), "constants are compared by name, whatever their enum");
    }
}
