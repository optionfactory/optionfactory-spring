package net.optionfactory.context.propertysources;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.PropertySource;

/**
 * Read application properties from the following locations:
 * <ol>
 * <li>{@code project.properties} from classpath</li>
 * <li>{@code ${project.name}.properties} from classpath</li>
 * <li>{@code ~/.${project.name}.properties} (e.g. for development environment overrides)</li>
 * <li>{@code /opt/${project.name}/conf/project.properties} (e.g. for testing/production environment overrides, such as jdbc properties)</li>
 * </ol>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@PropertySource(value = "classpath:project.properties")
@PropertySource(value = "classpath:${project.name}.properties")
@PropertySource(value = "file:${user.home}/.${project.name}.properties", ignoreResourceNotFound = true)
@PropertySource(value = "file:/opt/${project.name}/conf/project.properties", ignoreResourceNotFound = true)
@Documented
public @interface ApplicationProperties {
}
