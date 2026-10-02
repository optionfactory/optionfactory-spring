package net.optionfactory.spring.downstream.plugin.emit.java;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.optionfactory.spring.downstream.Downstream;
import net.optionfactory.spring.downstream.plugin.emit.SourceEmitter.GenerateOutcome;
import net.optionfactory.spring.downstream.plugin.emit.java.JavaEmitter.DtoStyle;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaEmitterTest {

    public static class Order {

        public @NonNull String id;
        public Line[] lines;
        public Status status;

        public static class Line {

            public int quantity;
        }

        public enum Status {
            OPEN, CLOSED
        }
    }

    @Downstream.Rename("Customer")
    public record Client(String name) {

    }

    private static String read(File dir, String path) throws Exception {
        return Files.readString(new File(dir, path).toPath());
    }

    @Test
    public void nestedTypesAreEmittedInsideTheirParentWithNested(@TempDir File tempDir) throws Exception {
        final var registry = new TypeRegistry(Set.of(Order.class, Order.Line.class, Order.Status.class), "net.generated", Nesting.NESTED);
        final var outcomes = new JavaEmitter(tempDir, tempDir, Map.of(), DtoStyle.CLASSES, Set.of()).emit(registry);

        Assertions.assertEquals(List.of(new GenerateOutcome("src/main/java/net/generated/Order.java", true)), outcomes, "only top-level types get a file, nested ones are part of it");
        final var content = read(tempDir, "net/generated/Order.java");
        Assertions.assertTrue(content.contains("public static class Line {"), "a nested dto is a static nested class");
        Assertions.assertTrue(content.contains("public enum Status {"), "a nested enum is emitted inside the parent");
        Assertions.assertTrue(content.contains("public Line[] lines;"), "fields reference the nested generated types");
        Assertions.assertTrue(content.contains("@NonNull\n  public String id;"), "a non null field keeps a jspecify @NonNull");
        Assertions.assertTrue(content.contains("Mapped from {@code " + Order.class.getName() + "}"), "the top-level type documents its source class");
    }

    @Test
    public void nestedTypesGetTheirOwnFileWithFlatten(@TempDir File tempDir) throws Exception {
        final var registry = new TypeRegistry(Set.of(Order.class, Order.Line.class, Order.Status.class), "net.generated", Nesting.FLATTEN);
        new JavaEmitter(tempDir, tempDir, Map.of(), DtoStyle.RECORDS, Set.of()).emit(registry);

        Assertions.assertTrue(new File(tempDir, "net/generated/Line.java").exists(), "a flattened nested dto gets its own file");
        Assertions.assertTrue(new File(tempDir, "net/generated/Status.java").exists(), "a flattened nested enum gets its own file");
        Assertions.assertFalse(read(tempDir, "net/generated/Order.java").contains("class Line"), "a flattened nested dto is not repeated inside its parent");
    }

    @Test
    public void renamedTypesAreEmittedAndReferencedUnderTheNewName(@TempDir File tempDir) throws Exception {
        final var registry = new TypeRegistry(Set.of(Client.class), "net.generated", Nesting.FLATTEN);
        new JavaEmitter(tempDir, tempDir, Map.of(), DtoStyle.RECORDS, Set.of()).emit(registry);

        Assertions.assertTrue(read(tempDir, "net/generated/Customer.java").contains("public record Customer(String name)"), "@Downstream.Rename sets the generated simple name");
    }

    @Test
    public void typesAlreadyWrittenByHandInTheProjectAreSkipped(@TempDir File tempDir) throws Exception {
        final var outputDir = new File(tempDir, "target/generated-sources");
        final var handWritten = new File(tempDir, "src/main/java/net/generated/Customer.java");
        handWritten.getParentFile().mkdirs();
        Files.writeString(handWritten.toPath(), "hand written");

        final var registry = new TypeRegistry(Set.of(Client.class), "net.generated", Nesting.FLATTEN);
        final var outcomes = new JavaEmitter(outputDir, tempDir, Map.of(), DtoStyle.RECORDS, Set.of()).emit(registry);

        Assertions.assertEquals(List.of(new GenerateOutcome("src/main/java/net/generated/Customer.java", false)), outcomes, "a type with a source file in the project is reported as skipped");
        Assertions.assertFalse(new File(outputDir, "net/generated/Customer.java").exists(), "a type with a source file in the project is not generated");
    }
}
