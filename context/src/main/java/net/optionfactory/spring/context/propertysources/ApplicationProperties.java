package net.optionfactory.spring.context.propertysources;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.PropertySource;

/// Registers the application property sources, in increasing order of precedence:
///
/// 1. `classpath:project.properties`, where the properties filtered by maven belong (git
///    information included);
/// 2. `classpath:${project.name}.properties`, for the unfiltered properties;
/// 3. `file:${user.home}/.${project.name}.properties`, for overrides on a developer machine (use
///    this feature responsibly);
/// 4. `file:/opt/${project.name}/conf/project.properties`, for the overrides of a testing or
///    production environment, such as the jdbc properties.
///
/// A property defined in a later source overrides the same property in an earlier one. Both
/// classpath sources are mandatory and fail the context when missing, while the two file sources
/// are skipped when absent.
///
/// `project.name` is resolved against the environment as each source is registered, so besides a
/// system property or an environment variable it can come from `project.properties` itself, where
/// maven filtering typically puts it.
///
/// The annotation registers the sources only: `@Value` placeholders also need a
/// `PropertySourcesPlaceholderConfigurer`, which [ApplicationPropertiesConfig] provides.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@PropertySource(value = "classpath:project.properties", encoding = "UTF-8")
@PropertySource(value = "classpath:${project.name}.properties", encoding = "UTF-8")
@PropertySource(value = "file:${user.home}/.${project.name}.properties", encoding = "UTF-8", ignoreResourceNotFound = true)
@PropertySource(value = "file:/opt/${project.name}/conf/project.properties", encoding = "UTF-8", ignoreResourceNotFound = true)
@Documented
public @interface ApplicationProperties {
}
