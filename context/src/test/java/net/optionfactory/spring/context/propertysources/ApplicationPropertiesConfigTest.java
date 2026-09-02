package net.optionfactory.spring.context.propertysources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class ApplicationPropertiesConfigTest {

    @AfterEach
    public void cleanup() {
        System.clearProperty("project.name");
    }

    @Configuration
    public static class ConsumingConfig {

        @Value("${context.test.project}")
        String projectProperty;

        @Value("${context.test.module}")
        String moduleProperty;

        @Bean
        public String projectProperty() {
            return projectProperty;
        }

        @Bean
        public String moduleProperty() {
            return moduleProperty;
        }

    }

    @Test
    public void propertiesFromBothClasspathSourcesAreExposedAndPlaceholderResolvable() {
        System.setProperty("project.name", "context-test");
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(ApplicationPropertiesConfig.class, ConsumingConfig.class);
            ctx.refresh();

            Assertions.assertEquals("from-project-properties", ctx.getEnvironment().getProperty("context.test.project"));
            Assertions.assertEquals("from-module-properties", ctx.getEnvironment().getProperty("context.test.module"));

            Assertions.assertEquals("from-project-properties", ctx.getBean("projectProperty", String.class));
            Assertions.assertEquals("from-module-properties", ctx.getBean("moduleProperty", String.class));
        }
    }
}
