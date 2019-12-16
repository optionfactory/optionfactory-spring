package net.optionfactory.context.propertysources;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

/**
 * Read application properties from the following locations:
 *
 * <ol>
 * <li>{@code project.properties} from classpath, where properties filtered by maven should be placed;</li>
 * <li>{@code ${project.name}.properties} from classpath, containing unfiltered properties;</li>
 * <li>{@code git.properties} from classpath, containing git information generated by pl.project13.maven:git-commit-id-plugin;</li>
 * <li>{@code ~/.${project.name}.properties}, for local development environment overrides (use this feature responsibly);</li>
 * <li>{@code /opt/${project.name}/conf/project.properties}, for testing/production environment overrides, such as jdbc properties.</li>
 * </ol>
 *
 * Moreover, enables property placeholders (e.g. {@code @Value("${...}")})
 * replacement using the above properties sources.
 *
 * Import this configuration class in every Spring context that requires such
 * application properties.
 */
@Configuration
@ApplicationProperties
public class ApplicationPropertiesConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
