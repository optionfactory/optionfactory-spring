package net.optionfactory.spring.downstream.plugin;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import net.optionfactory.spring.downstream.plugin.emit.java.JavaEmitter;
import net.optionfactory.spring.downstream.plugin.emit.java.JavaEmitter.DtoStyle;
import net.optionfactory.spring.downstream.plugin.emit.ts.TypeScriptEmitter;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RecordWithNullableFieldTest {

    public record Account(@Nullable String username) {

    }

    @Test
    public void shouldEmitTypescriptNullableFieldFromNullableRecordComponent(@TempDir File tempDir) throws Exception {
        final Set<Class<?>> payloads = Set.of(Account.class);
        final var registry = new TypeRegistry(payloads, "net.generated", Nesting.FLATTEN);

        final var emitter = new TypeScriptEmitter(tempDir, Map.of(), Map.of());
        emitter.emit(registry);

        final var file = new File(tempDir, "spec.d.ts");
        Assertions.assertTrue(file.exists());

        final var content = Files.readString(file.toPath());

        Assertions.assertTrue(content.contains("username?: string;"), "TS emitter missed the optional/nullable signifier on the record component");
    }

    @Test
    public void shouldEmitJavaNullableFieldFromNullableRecordComponent(@TempDir File tempDir) throws Exception {
        final Set<Class<?>> payloads = Set.of(Account.class);
        final var registry = new TypeRegistry(payloads, "net.generated", Nesting.FLATTEN);

        final var emitter = new JavaEmitter(tempDir, tempDir, Map.of(), DtoStyle.RECORDS, Set.of());
        emitter.emit(registry);

        final var file = new File(tempDir, "net/generated/Account.java");
        Assertions.assertTrue(file.exists());

        final var content = Files.readString(file.toPath());
        Assertions.assertTrue(content.contains("public record Account(@Nullable String username) {"), "Java emitter failed to output standard record structural shell syntax");
    }
}
