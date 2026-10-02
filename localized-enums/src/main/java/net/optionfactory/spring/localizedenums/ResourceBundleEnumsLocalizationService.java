package net.optionfactory.spring.localizedenums;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/// Translates enum constants through a spring [ResourceBundleMessageSource], looking each one up
/// under the `<prefix>.<category>.<NAME>` code.
///
/// The enums annotated with [LocalizedEnum] are found once, at construction, by scanning the
/// package of a root class and its subpackages; they are what
/// [#values(Optional, Locale)] lists. Translations are not cached here: each call asks the message
/// source, which applies its own caching and locale fallback rules. The service is immutable, and
/// as thread-safe as the message source.
///
/// A constant is translated under the category of the enum that declares it, also when the
/// constant has a body (and therefore a class of its own, which does not carry the annotation).
///
/// ```java
/// final var source = new ResourceBundleMessageSource();
/// source.setDefaultEncoding(StandardCharsets.UTF_8.name());
/// source.setBasenames("localization");
/// final var les = new ResourceBundleEnumsLocalizationService("enums", source, OrderStatus.class, ResolutionMode.MISSING_AS_NAME);
/// les.value(EnumKey.of("order-status", "SHIPPED"), Locale.ITALIAN);
/// ```
public class ResourceBundleEnumsLocalizationService implements EnumsLocalizationService {

    private final String prefix;
    private final List<EnumKey> keys;
    private final ResourceBundleMessageSource bundle;
    private final ResolutionMode mode;

    /// What a missing translation resolves to.
    ///
    /// The mode is the default message of the lookup, so it only applies when the message source
    /// itself has no fallback: with `setUseCodeAsDefaultMessage(true)`, a missing translation
    /// resolves to the bundle code whatever the mode.
    public enum ResolutionMode {
        /// A missing translation resolves to the name of the constant.
        MISSING_AS_NAME,
        /// A missing translation resolves to nothing: an empty [Optional], or a `null` value.
        MISSING_AS_NULL;
    }

    /// Scans for the localized enums and creates the service.
    ///
    /// @param prefix the first segment of every bundle code
    /// @param bundle the message source holding the translations
    /// @param root a class whose package, subpackages included, is scanned for [LocalizedEnum]
    /// enums
    /// @param mode what a missing translation resolves to
    /// @throws ClassCastException when a scanned class annotated with [LocalizedEnum] is not an
    /// enum
    public ResourceBundleEnumsLocalizationService(String prefix, ResourceBundleMessageSource bundle, Class<?> root, ResolutionMode mode) {
        final var cps = new ClassPathScanningCandidateComponentProvider(false);
        cps.addIncludeFilter(new AnnotationTypeFilter(LocalizedEnum.class));

        final List<Enum> enums = cps.findCandidateComponents(root.getPackageName())
                .stream()
                .map(BeanDefinition::getBeanClassName)
                .map(ResourceBundleEnumsLocalizationService::enumForName)
                .map(Class::getEnumConstants)
                .flatMap(Arrays::stream)
                .collect(Collectors.toList());

        this.keys = enums.stream().map(this::enumValueToEnumKey).collect(Collectors.toList());
        this.prefix = prefix;
        this.bundle = bundle;
        this.mode = mode;

    }

    /// The enum need not be scanned nor annotated: without [LocalizedEnum] its category is its
    /// simple name.
    ///
    /// @param category the enum class
    /// @param locale the locale of the translations
    /// @return the constants in declaration order, with their translations
    @Override
    public List<LocalizedEnumResponse> values(Class<Enum<?>> category, Locale locale) {
        return Arrays.stream(category.getEnumConstants()).map(this::enumValueToEnumKey).map(ek -> ek.toLabel(resolve(bundle, ek, locale))).collect(Collectors.toList());
    }

    /// Lists the constants of the scanned enums only.
    ///
    /// @param category the category to list, or empty for every scanned enum
    /// @param locale the locale of the translations
    /// @return the constants in declaration order, enum by enum in scanning order, or an empty list
    /// for a category no scanned enum has
    @Override
    public List<LocalizedEnumResponse> values(Optional<String> category, Locale locale) {
        return keys.stream().filter(sc -> category.map(t -> t.equals(sc.category())).orElse(true)).map(ek -> ek.toLabel(resolve(bundle, ek, locale))).collect(Collectors.toList());
    }

    /// The key is not checked against the scanned enums.
    ///
    /// @param key the category and name of the constant
    /// @param locale the locale of the translation
    /// @return the translation; when it is missing, the name for [ResolutionMode#MISSING_AS_NAME]
    /// and empty for [ResolutionMode#MISSING_AS_NULL]
    @Override
    public Optional<String> value(EnumKey key, Locale locale) {
        return Optional.ofNullable(resolve(bundle, key, locale));
    }
    
    private EnumKey enumValueToEnumKey(Enum enumValue) {
        final Class<?> type = enumValue.getDeclaringClass();
        final LocalizedEnum md = type.getAnnotation(LocalizedEnum.class);
        final String category = md == null || md.category().isBlank() ? type.getSimpleName() : md.category();
        return EnumKey.of(category, enumValue.name());
    }

    private String resolve(ResourceBundleMessageSource bundle, EnumKey ek, Locale locale) {
        final String bundleCode = String.format("%s.%s.%s", this.prefix, ek.category(), ek.name());
        final Object[] args = new Object[0];
        final String defaultMessage = mode == ResolutionMode.MISSING_AS_NAME ? ek.name() : null;
        return bundle.getMessage(bundleCode, args, defaultMessage, locale);
    }

    private static Class<? extends Enum> enumForName(String name) {
        try {
            return Class.forName(name).asSubclass(Enum.class);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
