package net.optionfactory.spring.localizedenums;

/// Identifies an enum constant to translate: the category of its enum and the constant's name.
///
/// The category is the one of [LocalizedEnum], or the enum's simple name when the annotation is
/// missing or has a blank category. A key is not checked against the scanned enums: any category
/// and name can be looked up, and the translation is resolved from the bundle alone.
///
/// @param category the category of the enum
/// @param name the name of the constant, as [Enum#name()]
public record EnumKey(String category, String name) {

    /// @param category the category of the enum
    /// @param name the name of the constant
    /// @return the key
    public static EnumKey of(String category, String name) {
        return new EnumKey(category, name);
    }

    /// Pairs this key with its translation.
    ///
    /// @param value the translation, possibly `null`
    /// @return the localized constant
    public LocalizedEnumResponse toLabel(String value) {
        return LocalizedEnumResponse.of(category, name, value);
    }
}
