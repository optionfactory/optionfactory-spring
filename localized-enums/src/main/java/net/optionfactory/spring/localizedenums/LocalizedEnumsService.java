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

public class LocalizedEnumsService {

    private final String prefix;
    private final List<EnumKey> keys;
    private final ResourceBundleMessageSource bundle;
    private final ResolutionMode mode;

    public enum ResolutionMode {
        MISSING_AS_NAME, MISSING_AS_NULL;
    }

    public LocalizedEnumsService(String prefix, ResourceBundleMessageSource bundle, Class<?> root, ResolutionMode mode) {
        final var cps = new ClassPathScanningCandidateComponentProvider(false);
        cps.addIncludeFilter(new AnnotationTypeFilter(LocalizedEnum.class));

        final List<Enum> enums = cps.findCandidateComponents(root.getPackageName())
                .stream()
                .map(BeanDefinition::getBeanClassName)
                .map(LocalizedEnumsService::enumForName)
                .map(Class::getEnumConstants)
                .flatMap(Arrays::stream)
                .collect(Collectors.toList());

        this.keys = enums.stream()
                .map(enumValue -> {
                    final LocalizedEnum md = enumValue.getClass().getAnnotation(LocalizedEnum.class);
                    final String category = md.category().isBlank() ? enumValue.getDeclaringClass().getSimpleName() : md.category();
                    return EnumKey.of(category, enumValue.name());
                }).collect(Collectors.toList());
        this.prefix = prefix;
        this.bundle = bundle;
        this.mode = mode;

    }

    public List<LocalizedEnumResponse> search(Optional<String> category, Locale locale) {
        return keys.stream()
                .filter(sc -> category.map(t -> t.equals(sc.category)).orElse(true))
                .map(ek -> ek.toLabel(resolve(bundle, ek, locale)))
                .collect(Collectors.toList());
    }

    public Optional<String> translate(EnumKey key, Locale locale) {
        return Optional.ofNullable(resolve(bundle, key, locale));
    }

    private String resolve(ResourceBundleMessageSource bundle, EnumKey ek, Locale locale) {
        final String bundleCode = String.format("%s.%s.%s", this.prefix, ek.category, ek.name);
        final Object[] args = new Object[0];
        final String defaultMessage = mode == ResolutionMode.MISSING_AS_NAME ? ek.name : null;
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
