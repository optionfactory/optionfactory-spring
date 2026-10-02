package net.optionfactory.spring.localizedenums.dialects;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.optionfactory.spring.localizedenums.EnumKey;
import net.optionfactory.spring.localizedenums.EnumsLocalizationService;
import net.optionfactory.spring.localizedenums.LocalizedEnumResponse;
import org.springframework.context.i18n.LocaleContextHolder;

/// Template-friendly functions over an [EnumsLocalizationService], translating in the locale of
/// the current thread as held by spring's `LocaleContextHolder` (the request locale, in a spring
/// mvc request).
///
/// Meant to be exposed to thymeleaf templates, e.g. through the `SingletonDialect` of the
/// `thymeleaf` module:
///
/// ```java
/// @Bean
/// public SingletonDialect localizedEnumsDialect(EnumsLocalizationService les) {
///     return SingletonDialect.of("enums", new LocalizedEnums(les));
/// }
/// ```
///
/// ```html
/// <span th:text="${#enums.value('order-status', order.status.name())}"></span>
/// ```
public class LocalizedEnums {

    private final EnumsLocalizationService les;

    /// @param les the service translating the constants
    public LocalizedEnums(EnumsLocalizationService les) {
        this.les = les;
    }

    /// Translates one constant.
    ///
    /// @param category the category of the enum
    /// @param name the name of the constant
    /// @return the translation
    /// @throws java.util.NoSuchElementException when the translation is missing and the service
    /// resolves missing translations to nothing
    public String value(String category, String name) {
        return les.value(EnumKey.of(category, name), LocaleContextHolder.getLocale()).orElseThrow();
    }

    /// @param enumClass the enum class, annotated or not
    /// @return its constants in declaration order, with their translations
    public List<LocalizedEnumResponse> values(Class<Enum<?>> enumClass) {
        return les.values(enumClass, LocaleContextHolder.getLocale());
    }

    /// @param category the category to list
    /// @return the constants of the category known to the service, with their translations, or
    /// an empty list for an unknown category
    public List<LocalizedEnumResponse> values(String category) {
        return les.values(Optional.of(category), LocaleContextHolder.getLocale());
    }

    /// Tells whether a collection holds the constant, e.g. to mark the selected options of a
    /// multiple select.
    ///
    /// Constants are compared by name only, ignoring the category: a constant of another enum with
    /// the same name matches too.
    ///
    /// @param le the localized constant
    /// @param haystack the constants to search
    /// @return true when a constant of the collection has the same name
    public boolean in(LocalizedEnumResponse le, Collection<Enum<?>> haystack) {
        return haystack.stream().anyMatch(e -> e.name().equals(le.name()));
    }

}
