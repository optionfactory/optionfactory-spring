package net.optionfactory.spring.localizedenums;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.optionfactory.spring.localizedenums.ResourceBundleEnumsLocalizationService.ResolutionMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import net.optionfactory.spring.localizedenums.dialects.LocalizedEnums;
import org.springframework.context.support.ResourceBundleMessageSource;

public class EnumsLocalizazionServiceTest {

    @Test
    public void annotatedEnumsAreScanned() {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        final List<LocalizedEnumResponse> result = ecs.values(Optional.empty(), Locale.ENGLISH);
        final boolean foundExpectedTranslation = result.stream()
                .anyMatch(r -> "AnEnum".equals(r.category()) && "VALUE_1".equals(r.name()) && "Translated".equals(r.value()));

        Assertions.assertTrue(foundExpectedTranslation, "the annotated AnEnum is scanned and translated under its simple name");
    }

    @Test
    public void canTranslate() {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        Assertions.assertEquals(Optional.of("Translated"), ecs.value(EnumKey.of("AnEnum", "VALUE_1"), Locale.ENGLISH), "the translation is read from <prefix>.<category>.<NAME>");
    }

    @Test
    public void whenResolutionModeIsMissingAsNameTranslatingMissingValuesYieldsName() {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        Assertions.assertEquals(Optional.of("NOT_THERE"), ecs.value(EnumKey.of("AnEnum", "NOT_THERE"), Locale.ENGLISH), "a missing translation resolves to the name");
    }

    @Test
    public void whenResolutionModeIsMissingAsNameTranslatingMissingCategoryYieldsName() {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        Assertions.assertEquals(Optional.of("NOT_THERE"), ecs.value(EnumKey.of("NotAnnotated", "NOT_THERE"), Locale.ENGLISH), "a key of an unknown category resolves to the name too");
    }

    @Test
    public void enumsWithConstantBodiesAreScannedUnderTheirDeclaredCategory() {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        final List<LocalizedEnumResponse> result = ecs.values(Optional.of("with-bodies"), Locale.ENGLISH);

        Assertions.assertEquals(List.of(
                LocalizedEnumResponse.of("with-bodies", "PLAIN", "PLAIN"),
                LocalizedEnumResponse.of("with-bodies", "WITH_BODY", "With a body")
        ), result, "constants with a body are listed under the category of their declaring enum");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void valuesOfAnEnumClassWithConstantBodiesUseItsDeclaredCategory() {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        final var enumClass = (Class<Enum<?>>) (Class<?>) AnEnumWithBodies.class;
        Assertions.assertEquals("With a body", ecs.values(enumClass, Locale.ENGLISH).get(1).value(), "a constant with a body is translated under the category of its declaring enum");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void valuesOfANotAnnotatedEnumClassUseItsSimpleNameAsCategory() {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        final var enumClass = (Class<Enum<?>>) (Class<?>) NotAnnotatedEnum.class;
        Assertions.assertEquals(List.of(LocalizedEnumResponse.of("NotAnnotatedEnum", "ONE", "One")), ecs.values(enumClass, Locale.ENGLISH), "a not annotated enum is categorized by its simple name");
    }

    private static ResourceBundleMessageSource localization() {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    @Test
    public void whenResolutionModeIsMissingAsNullTranslatingMissingValuesYieldsEmpty() {
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", localization(), AnEnum.class, ResolutionMode.MISSING_AS_NULL);

        Assertions.assertEquals(Optional.empty(), ecs.value(EnumKey.of("AnEnum", "VALUE_2"), Locale.ENGLISH), "a missing translation resolves to nothing");
    }

    @Test
    public void whenResolutionModeIsMissingAsNullListedMissingValuesAreNull() {
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", localization(), AnEnum.class, ResolutionMode.MISSING_AS_NULL);

        Assertions.assertEquals(List.of(
                LocalizedEnumResponse.of("AnEnum", "VALUE_1", "Translated"),
                LocalizedEnumResponse.of("AnEnum", "VALUE_2", null)
        ), ecs.values(Optional.of("AnEnum"), Locale.ENGLISH), "a missing translation is listed with a null value");
    }

    @Test
    public void useCodeAsDefaultMessageTakesPrecedenceOverMissingAsNull() {
        final var source = localization();
        source.setUseCodeAsDefaultMessage(true);
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NULL);

        Assertions.assertEquals(Optional.of("enums.AnEnum.VALUE_2"), ecs.value(EnumKey.of("AnEnum", "VALUE_2"), Locale.ENGLISH), "the message source falls back to the bundle code before the mode applies");
    }

    @Test
    public void valuesOfAnUnknownCategoryAreEmpty() {
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", localization(), AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        Assertions.assertEquals(List.of(), ecs.values(Optional.of("unknown"), Locale.ENGLISH), "no scanned enum has the category");
    }

    @Test
    public void valuesOfAllCategoriesListOnlyAnnotatedEnums() {
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", localization(), AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        final var categories = ecs.values(Optional.empty(), Locale.ENGLISH).stream().map(LocalizedEnumResponse::category).distinct().sorted().toList();
        Assertions.assertEquals(List.of("AnEnum", "with-bodies"), categories, "not annotated enums are not scanned, even when translated in the bundle");
    }

    @Test
    public void onlyThePackageOfTheRootAndItsSubpackagesAreScanned() {
        final var ecs = new ResourceBundleEnumsLocalizationService("enums", localization(), LocalizedEnums.class, ResolutionMode.MISSING_AS_NAME);

        Assertions.assertEquals(List.of(), ecs.values(Optional.empty(), Locale.ENGLISH), "the enums of the parent package are out of the scan");
    }

    @Test
    public void thePrefixIsTheFirstSegmentOfTheBundleCode() {
        final var ecs = new ResourceBundleEnumsLocalizationService("other", localization(), AnEnum.class, ResolutionMode.MISSING_AS_NULL);

        Assertions.assertEquals(Optional.empty(), ecs.value(EnumKey.of("AnEnum", "VALUE_1"), Locale.ENGLISH), "the translation lives under enums.*, not other.*");
    }

}
