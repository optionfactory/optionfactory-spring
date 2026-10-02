package net.optionfactory.spring.context.propertysources;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

/// Registers the [ApplicationProperties] sources and resolves `${...}` placeholders (e.g. in
/// `@Value`) against them.
///
/// Import this configuration in every spring context that needs the application properties.
@Configuration
@ApplicationProperties
public class ApplicationPropertiesConfig {

    /// Declared `static` so that the configurer is instantiated before this configuration, and can
    /// therefore process the placeholders of every bean, this one's included.
    ///
    /// @return the placeholder configurer
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
