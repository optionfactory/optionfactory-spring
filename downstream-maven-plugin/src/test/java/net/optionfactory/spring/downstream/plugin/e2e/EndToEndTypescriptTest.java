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
                () -> Assertions.assertTrue(content.contains("export interface Page<T>"), "a generic payload becomes a generic interface"),
                () -> Assertions.assertTrue(content.contains("data: T[];"), "a generic array field becomes an array of the type variable"),
                () -> Assertions.assertTrue(content.contains("export interface User"), "a record becomes an interface"),
                () -> Assertions.assertTrue(content.contains("email?: string;"), "a @Nullable component becomes an optional property"),
                () -> Assertions.assertTrue(content.contains("export type Role = \"ADMIN\""), "an enum becomes a union of string literals")
        );
    }
}
