package net.optionfactory.spring.localizedenums.dialects;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.optionfactory.spring.localizedenums.EnumKey;
import net.optionfactory.spring.localizedenums.LocalizedEnumResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import net.optionfactory.spring.localizedenums.EnumsLocalizationService;

public class LocalizedEnums {

    private final EnumsLocalizationService les;

    public LocalizedEnums(EnumsLocalizationService les) {
        this.les = les;
    }

    public String value(String category, String name) {
        return les.value(EnumKey.of(category, name), LocaleContextHolder.getLocale()).orElseThrow();
    }

    public List<LocalizedEnumResponse> values(Class<Enum<?>> enumClass) {
        return les.values(enumClass, LocaleContextHolder.getLocale());
    }

    public List<LocalizedEnumResponse> values(String category) {
        return les.values(Optional.of(category), LocaleContextHolder.getLocale());
    }

    public boolean in(LocalizedEnumResponse le, Collection<Enum<?>> haystack) {
        return haystack.stream().anyMatch(e -> e.name().equals(le.name));
    }

}
