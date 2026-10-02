package net.optionfactory.spring.context.devtools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotationMetadata;

public class DevToolsImportSelectorTest {

    @Configuration
    @Import(DevToolsImportSelector.class)
    public static class ImportingConfig {

    }

    @Test
    public void withoutDevToolsOnTheClasspathNothingIsImported() {
        final var imports = new DevToolsImportSelector().selectImports(AnnotationMetadata.introspect(ImportingConfig.class));
        Assertions.assertEquals(0, imports.length, "devtools is not on the classpath: no configuration must be imported");
    }

    @Test
    public void aContextImportingTheSelectorStartsWithoutDevTools() {
        try (var ctx = new AnnotationConfigApplicationContext(ImportingConfig.class)) {
            Assertions.assertTrue(ctx.isActive(), "importing the selector must not require devtools at runtime");
        }
    }
}
