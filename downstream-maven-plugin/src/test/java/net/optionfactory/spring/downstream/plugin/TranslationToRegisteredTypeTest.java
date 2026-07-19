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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TranslationToRegisteredTypeTest {

    public static enum OldType {
        ADMIN;
    }

    public static enum NewType {
        ADMIN;        
    }

    public record User(OldType type){
    }

    @Test
    public void shouldMapTranslatedFieldToRegisteredTypeInTypeScript(@TempDir File tempDir) throws Exception {
        final var payloads = Set.of(NewType.class, User.class);
        final var registry = new TypeRegistry(payloads, "net.generated", Nesting.FLATTEN);

        final var translations = Map.of(
                OldType.class.getName(), NewType.class.getName()
        );

        final var emitter = new TypeScriptEmitter(tempDir, translations, Map.of());
        emitter.emit(registry);

        final var file = new File(tempDir, "spec.d.ts");
        Assertions.assertTrue(file.exists(), "TypeScript definition file was not generated");
        
        final var content = Files.readString(file.toPath());

        Assertions.assertTrue(content.contains("type: NewType;"), "TS Emitter failed to map translated OldUser to registered NewUser");
        Assertions.assertFalse(content.contains("type: any;"), "TS Emitter incorrectly fell back to 'any'");
    }

    @Test
    public void shouldMapTranslatedFieldToRegisteredTypeInJava(@TempDir File tempDir) throws Exception {
        final var payloads = Set.of(NewType.class, User.class);
        final var registry = new TypeRegistry(payloads, "net.generated", Nesting.FLATTEN);

        final var translations = Map.of(
                OldType.class.getName(), NewType.class.getName()
        );

        final var emitter = new JavaEmitter(tempDir, tempDir, translations, DtoStyle.RECORDS, Set.of());
        emitter.emit(registry);

        final var file = new File(tempDir, "net/generated/User.java");
        Assertions.assertTrue(file.exists(), "Java class file was not generated");

        final var content = Files.readString(file.toPath());

        Assertions.assertTrue(content.contains("NewType type"), "Java Emitter failed to map translated OldUser to generated NewUser");
        Assertions.assertFalse(content.contains(NewType.class.getName()), "Java Emitter incorrectly used the original backend class FQN");
    }
}