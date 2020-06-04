package net.optionfactory.spring.context.conditionals;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Registers the annotated {@code @Bean} or {@code @Component} only if the
 * property matches one value in the given set. The bean can be registered when
 * the property is absent by setting {@code matchesWhenMissing = true}. It can
 * be used to implement feature toggling in a declarative way.
 *
 * Given a set of properties in the context environment:
 *
 * <pre><code>
 *   prop.a = true
 *   prop.b = sad
 *   prop.c = happy
 * </code></pre>
 *
 * the following bean definitions are registered
 *
 * <pre><code>
 *   {@literal @}Bean
 *   {@literal @}ConditionalProperty(name = "prop.a")
 *   {@literal [...]}
 *
 *   {@literal @}Bean
 *   {@literal @}ConditionalProperty(name = "prop.b", matches = "sad")
 *   {@literal [...]}
 *
 *   {@literal @}Bean
 *   {@literal @}ConditionalProperty(name = "prop.c", matches = {"happy", "tired"})
 *   {@literal [...]}
 *
 *   {@literal @}Bean
 *   {@literal @}ConditionalProperty(name = "prop.d", matchesWhenMissing = true)
 *   {@literal [...]}
 * </code></pre>
 *
 * while the following bean definitions are discarded
 *
 * <pre><code>
 *   {@literal @}Bean
 *   {@literal @}ConditionalProperty(name = "prop.b") // "sad" does not match "true"
 *   {@literal [...]}
 *
 *   {@literal @}Bean
 *   {@literal @}ConditionalProperty(name = "prop.c", matches = {"sad", "tired"}) // none matches "happy"
 *   {@literal [...]}
 *
 *   {@literal @}Bean
 *   {@literal @}ConditionalProperty(name = "prop.d") // property is absent
 *   {@literal [...]}
 * </code></pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(ConditionalProperty.PropertyCondition.class)
public @interface ConditionalProperty {

    String name();

    String[] matches() default "true";

    boolean matchesWhenMissing() default false;

    public static class PropertyCondition implements ConfigurationCondition {

        private static final String ANNOTATION_NAME = ConditionalProperty.class.getName();

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            if (!metadata.isAnnotated(ANNOTATION_NAME)) {
                return true;
            }
            if (metadata.isAnnotated(Configuration.class.getName())) {
                throw new IllegalStateException(String.format("@%s cannot be applied to @%s classes at type level, properties are not accessible in this phase yet", ConditionalProperty.class.getSimpleName(), Configuration.class.getSimpleName()));
            }
            final Map<String, Object> attributes = metadata.getAnnotationAttributes(ANNOTATION_NAME);
            final String propertyName = (String) attributes.get("name");
            final String[] propertyMatches = (String[]) attributes.get("matches");
            final boolean propertyMatchesWhenMissing = (boolean) attributes.get("matchesWhenMissing");
            if (!context.getEnvironment().containsProperty(propertyName)) {
                return propertyMatchesWhenMissing;
            }
            final String propertyValue = context.getEnvironment().getProperty(propertyName);
            return Stream.of(propertyMatches).anyMatch(propertyValue::equals);
        }

        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }
    }
}
