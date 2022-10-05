package net.optionfactory.spring.localizedenums;

import java.util.List;
import java.util.Locale;
import java.util.Optional;


public interface EnumsLocalizationService {

    Optional<String> value(EnumKey key, Locale locale);

    List<LocalizedEnumResponse> values(Class<Enum<?>> category, Locale locale);

    List<LocalizedEnumResponse> values(Optional<String> category, Locale locale);
    
}
