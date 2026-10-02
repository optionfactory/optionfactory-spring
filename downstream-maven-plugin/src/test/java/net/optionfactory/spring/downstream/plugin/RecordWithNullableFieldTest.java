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
        Assertions.assertTrue(file.exists(), "the TypeScript spec file is generated");

        final var content = Files.readString(file.toPath());

        Assertions.assertTrue(content.contains("username?: string;"), "a @Nullable record component is an optional TypeScript property");
    }

    @Test
    public void shouldEmitJavaNullableFieldFromNullableRecordComponent(@TempDir File tempDir) throws Exception {
        final Set<Class<?>> payloads = Set.of(Account.class);
        final var registry = new TypeRegistry(payloads, "net.generated", Nesting.FLATTEN);

        final var emitter = new JavaEmitter(tempDir, tempDir, Map.of(), DtoStyle.RECORDS, Set.of());
        emitter.emit(registry);

        final var file = new File(tempDir, "net/generated/Account.java");
        Assertions.assertTrue(file.exists(), "the Java source file is generated");

        final var content = Files.readString(file.toPath());
        Assertions.assertTrue(content.contains("public record Account(@Nullable String username) {"), "a @Nullable record component keeps @Nullable on the generated record component");
    }
}
