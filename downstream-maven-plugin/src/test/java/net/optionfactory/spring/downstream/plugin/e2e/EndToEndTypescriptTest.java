package net.optionfactory.spring.downstream.plugin.e2e;

import java.io.File;
import java.util.Map;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class EndToEndTypescriptTest {

    private final TestPipeline pipeline = new TestPipeline(
            ScanTarget.class.getPackageName(),
            "net.generated",
            "test-client"
    );

    @Test
    public void typescriptGeneration(@TempDir File tempDir) throws Exception {

        final var content = pipeline.typescript(tempDir, Nesting.FLATTEN, Map.of(), Map.of());

        Assertions.assertAll(
                () -> Assertions.assertTrue(content.contains("export interface Page<T>"), "missing generic wrapper"),
                () -> Assertions.assertTrue(content.contains("data: T[];"), "missing generic array field"),
                () -> Assertions.assertTrue(content.contains("export interface User"), "missing record mapped to interface"),
                () -> Assertions.assertTrue(content.contains("email?: string;"), "missing optional field mapping"),
                () -> Assertions.assertTrue(content.contains("export type Role = \"ADMIN\""), "missing enum definition")
        );
    }
}
