package net.optionfactory.spring.localizedenums;

/// An enum constant with its translation, ready to be serialized for a client (e.g. the options of
/// a select, or a translations endpoint).
///
/// @param category the category of the enum, see [EnumKey]
/// @param name the name of the constant
/// @param value the translation; the name, or `null`, when it is missing, depending on the
/// [ResourceBundleEnumsLocalizationService.ResolutionMode]
public record LocalizedEnumResponse(String category, String name, String value) {

    /// @param category the category of the enum
    /// @param name the name of the constant
    /// @param value the translation
    /// @return the localized constant
    public static LocalizedEnumResponse of(String category, String name, String value) {
        return new LocalizedEnumResponse(category, name, value);
    }
}
