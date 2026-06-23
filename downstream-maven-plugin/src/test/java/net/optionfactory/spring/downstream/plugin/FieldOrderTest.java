package net.optionfactory.spring.downstream.plugin;

import net.optionfactory.spring.downstream.plugin.emit.ts.TypeScriptEmitter;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class FieldOrderTest {
    static class ExampleDto {
        public String a;
        public String b;
        public String c;
        public String d;
        public String e;
        public String f;
        public String g;
        public String h;
    }

    @Test
    public void shouldGenerateFieldsInSameOrderOfGetDeclaredFields(@TempDir File tempDir) throws Exception {
        final Set<Class<?>> payloads = Set.of(ExampleDto.class);
        final var registry = new TypeRegistry(payloads, "net.generated", Nesting.FLATTEN);
        final var emitter = new TypeScriptEmitter(tempDir, Collections.emptyMap(), Collections.emptyMap());
        emitter.emit(registry);

        final File generatedFile = new File(tempDir, "spec.d.ts");
        Assertions.assertTrue(generatedFile.exists(), "TypeScript definition file was not generated");
        final String contents = Files.readString(generatedFile.toPath());

        var actual = contents.lines()
                .filter(l -> l.endsWith(": string;"))
                .map(l -> l.trim().substring(0, 1))
                .toList();

        var expected = Arrays.stream(ExampleDto.class.getDeclaredFields()).map(Field::getName).toList();
        Assertions.assertEquals(expected, actual);
    }
}


