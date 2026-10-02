package net.optionfactory.spring.context.propertysources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class ApplicationPropertiesConfigTest {

    private final String originalUserHome = System.getProperty("user.home");

    @TempDir
    Path home;

    @AfterEach
    public void cleanup() {
        System.clearProperty("project.name");
        System.setProperty("user.home", originalUserHome);
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

    private static AnnotationConfigApplicationContext refreshed() {
        final var ctx = new AnnotationConfigApplicationContext();
        ctx.register(ApplicationPropertiesConfig.class, ConsumingConfig.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    public void propertiesFromBothClasspathSourcesAreExposedAndPlaceholderResolvable() {
        try (var ctx = refreshed()) {
            Assertions.assertEquals("from-project-properties", ctx.getEnvironment().getProperty("context.test.project"), "project.properties must be a property source");
            Assertions.assertEquals("from-module-properties", ctx.getEnvironment().getProperty("context.test.module"), "${project.name}.properties must be a property source");
            Assertions.assertEquals("from-project-properties", ctx.getBean("projectProperty", String.class), "@Value placeholders must resolve against project.properties");
            Assertions.assertEquals("from-module-properties", ctx.getBean("moduleProperty", String.class), "@Value placeholders must resolve against ${project.name}.properties");
        }
    }

    @Test
    public void projectNameFromASystemPropertyWinsOverProjectProperties() {
        System.setProperty("project.name", "context-system-property");
        try (var ctx = refreshed()) {
            Assertions.assertEquals("from-system-property-module", ctx.getBean("moduleProperty", String.class), "the module properties must be read from the file named by the project.name system property");
        }
    }

    @Test
    public void userHomeFileOverridesTheClasspathSources() throws IOException {
        Files.writeString(home.resolve(".context-test.properties"), "context.test.module=from-user-home\n");
        System.setProperty("user.home", home.toString());
        try (var ctx = refreshed()) {
            Assertions.assertEquals("from-user-home", ctx.getBean("moduleProperty", String.class), "~/.${project.name}.properties must override the classpath sources");
            Assertions.assertEquals("from-project-properties", ctx.getBean("projectProperty", String.class), "a property the override does not define must still come from the classpath");
        }
    }
}
