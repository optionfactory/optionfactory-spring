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

public class TypeScriptGenericStrippingTest {

    public static class CustomWrapper<T> {

        public T value;
    }

    public static class PayloadDto {

        public CustomWrapper<String> metadata;
    }

    @Test
    public void shouldStripTypeParametersWhenGenericIsTranslatedToNonGeneric(@TempDir File tempDir) throws Exception {
        final Set<Class<?>> payloads = Set.of(PayloadDto.class);
        final var registry = new TypeRegistry(payloads, "net.generated", Nesting.FLATTEN);

        final var translations = Map.of(
                "net.optionfactory.spring.downstream.plugin.TypeScriptGenericStrippingTest$CustomWrapper", "java.lang.String"
        );
        
        final var emitter = new TypeScriptEmitter(tempDir, translations, Map.of());
        emitter.emit(registry);

        final var file = new File(tempDir, "spec.d.ts");
        Assertions.assertTrue(file.exists(), "TypeScript definition file was not generated");

        final var content = Files.readString(file.toPath());

        Assertions.assertTrue(content.contains("metadata: string;"), "TS Emitter failed to map the translation down to a standard primitive string");
        Assertions.assertFalse(content.contains("string<"), "TS Emitter left invalid generic bracket arguments appended to the field");
    }
}
