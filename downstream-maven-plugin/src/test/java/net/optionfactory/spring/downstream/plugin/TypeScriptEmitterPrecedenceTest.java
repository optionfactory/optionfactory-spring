package net.optionfactory.spring.downstream.plugin;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import net.optionfactory.spring.downstream.plugin.emit.ts.TypeScriptEmitter;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TypeScriptEmitterPrecedenceTest {

    static class ExampleDto {

        public java.util.UUID id;
    }

    @Test
    public void shouldPrioritizeTypeAliasOverGlobalJavaTranslation(@TempDir File tempDir) throws Exception {
        final Set<Class<?>> payloads = Set.of(ExampleDto.class);
        final var registry = new TypeRegistry(payloads, "net.generated", Nesting.FLATTEN);

        final var translations = Map.of("java.util.UUID", "java.lang.String");
        final var typeAliases = Map.of("java.util.UUID", "string");

        final var emitter = new TypeScriptEmitter(tempDir, translations, typeAliases);
        emitter.emit(registry);

        final File generatedFile = new File(tempDir, "spec.d.ts");
        Assertions.assertTrue(generatedFile.exists(), "TypeScript definition file was not generated");

        final String contents = Files.readString(generatedFile.toPath());

        Assertions.assertTrue(contents.contains("export type UUID = string;"), "TS Alias definitions missing from spec header");
        Assertions.assertTrue(contents.contains("id: UUID;"), "Emitter incorrectly dropped raw primitive instead of referencing explicit Alias override");
    }
}
