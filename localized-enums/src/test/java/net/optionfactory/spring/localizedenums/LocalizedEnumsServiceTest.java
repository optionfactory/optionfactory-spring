package net.optionfactory.spring.localizedenums;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.optionfactory.spring.localizedenums.LocalizedEnumsService.ResolutionMode;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

public class LocalizedEnumsServiceTest {

    @Test
    public void annotatedEnumsAreScanned() {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        final var ecs = new LocalizedEnumsService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        final List<LocalizedEnumResponse> result = ecs.search(Optional.empty(), Locale.ENGLISH);
        final boolean foundExpectedTranslation = result.stream()
                .anyMatch(r -> "AnEnum".equals(r.category) && "VALUE_1".equals(r.name) && "Translated".equals(r.value));

        Assert.assertTrue(foundExpectedTranslation);
    }

    @Test
    public void canTranslate() {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        final var ecs = new LocalizedEnumsService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        Assert.assertEquals(Optional.of("Translated"), ecs.translate(EnumKey.of("AnEnum", "VALUE_1"), Locale.ENGLISH));
    }

    @Test
    public void whenResolutionModeIsMissingAsNameTranslatingMissingValuesYieldsName() {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        final var ecs = new LocalizedEnumsService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        Assert.assertEquals(Optional.of("NOT_THERE"), ecs.translate(EnumKey.of("AnEnum", "NOT_THERE"), Locale.ENGLISH));
    }

    @Test
    public void whenResolutionModeIsMissingAsNameTranslatingMissingCategoryYieldsName() {
        final var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setBasenames("localization");
        source.setUseCodeAsDefaultMessage(false);
        final var ecs = new LocalizedEnumsService("enums", source, AnEnum.class, ResolutionMode.MISSING_AS_NAME);

        Assert.assertEquals(Optional.of("NOT_THERE"), ecs.translate(EnumKey.of("NotAnnotated", "NOT_THERE"), Locale.ENGLISH));
    }

}
