package net.optionfactory.spring.context.propertysources;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.PropertySource;

/**
 * Read application properties from the following locations:
 *
 * <ol>
 * <li>{@code project.properties} from classpath, where properties filtered by maven should be placed (incuding git properties)</li>
 * <li>{@code ${project.name}.properties} from classpath, containing unfiltered properties;</li>
 * <li>{@code ~/.${project.name}.properties}, for local development environment overrides (use this feature responsibly);</li>
 * <li>{@code /opt/${project.name}/conf/project.properties}, for testing/production environment overrides, such as jdbc properties.</li>
 * </ol>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@PropertySource(value = "classpath:project.properties", encoding = "UTF-8")
@PropertySource(value = "classpath:${project.name}.properties", encoding = "UTF-8")
@PropertySource(value = "file:${user.home}/.${project.name}.properties", encoding = "UTF-8", ignoreResourceNotFound = true)
@PropertySource(value = "file:/opt/${project.name}/conf/project.properties", encoding = "UTF-8", ignoreResourceNotFound = true)
@Documented
public @interface ApplicationProperties {
}
