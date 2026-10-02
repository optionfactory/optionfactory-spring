package net.optionfactory.spring.localizedenums;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// Translates enum constants, one at a time or as whole categories.
///
/// See [ResourceBundleEnumsLocalizationService] for the implementation backed by a resource
/// bundle.
public interface EnumsLocalizationService {

    /// Translates one constant.
    ///
    /// @param key the category and name of the constant
    /// @param locale the locale of the translation
    /// @return the translation, or empty when it is missing and the implementation resolves missing
    /// translations to nothing
    Optional<String> value(EnumKey key, Locale locale);

    /// Translates every constant of an enum, which need not be annotated with [LocalizedEnum] nor
    /// scanned.
    ///
    /// @param category the enum class
    /// @param locale the locale of the translations
    /// @return the constants in declaration order, with their translations
    List<LocalizedEnumResponse> values(Class<Enum<?>> category, Locale locale);

    /// Translates every known constant of a category, or of every category.
    ///
    /// @param category the category to list, or empty for all of them
    /// @param locale the locale of the translations
    /// @return the constants with their translations, or an empty list for an unknown category
    List<LocalizedEnumResponse> values(Optional<String> category, Locale locale);
    
}
