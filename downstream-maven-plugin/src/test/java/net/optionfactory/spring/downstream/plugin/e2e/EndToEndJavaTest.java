package net.optionfactory.spring.downstream.plugin.e2e;

import java.io.File;
import java.util.Map;
import net.optionfactory.spring.downstream.plugin.emit.java.JavaEmitter;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class EndToEndJavaTest {

    private final TestPipeline pipeline = new TestPipeline(
            ScanTarget.class.getPackageName(),
            "net.generated",
            "test-client"
    );

    @Test
    public void javaGeneration(@TempDir File tempDir) throws Exception {
        final var contents = pipeline.java(tempDir, Nesting.NESTED, JavaEmitter.DtoStyle.RECORDS, Map.of());
        Assertions.assertTrue(contents.get("net/generated/Page.java").contains("public record Page<T>("), "failed to output generic record");
        Assertions.assertTrue(contents.get("net/generated/Page.java").contains("T[] data"), "failed to output generic array field");
        Assertions.assertTrue(contents.get("net/generated/User.java").contains("@Nullable String email"), "failed to retain @Nullable annotation");
        Assertions.assertTrue(contents.get("net/generated/Role.java").contains("public enum Role {"), "failed to output enum struct");
        Assertions.assertTrue(contents.get("net/generated/Role.java").contains("ADMIN,"), "enum missing constants");
    }
}
