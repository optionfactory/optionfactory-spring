package net.optionfactory.spring.downstream.plugin;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import net.optionfactory.spring.downstream.plugin.emit.java.JavaEmitter;
import net.optionfactory.spring.downstream.plugin.emit.java.JavaEmitter.DtoStyle;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaEmitterOutputStyleOverrideTest {

    public record Account(String username) {

    }

    @Test
    public void overrideToClassesEmitsClassWhenDefaultIsRecords(@TempDir File tempDir) throws Exception {
        final Set<Class<?>> payloads = Set.of(Account.class);
        final var registry = new TypeRegistry(payloads, "net.generated", Nesting.FLATTEN);

        final var emitter = new JavaEmitter(tempDir, tempDir, Map.of(), DtoStyle.RECORDS, Set.of(Account.class.getName()));
        emitter.emit(registry);

        final var content = Files.readString(new File(tempDir, "net/generated/Account.java").toPath());
        Assertions.assertTrue(content.contains("public class Account"), "overridden dto should be emitted as a class");
        Assertions.assertTrue(content.contains("public String username;"), "overridden class dto should expose a public field");
    }

    @Test
    public void overrideToRecordsEmitsRecordWhenDefaultIsClasses(@TempDir File tempDir) throws Exception {
        final Set<Class<?>> payloads = Set.of(Account.class);
        final var registry = new TypeRegistry(payloads, "net.generated", Nesting.FLATTEN);

        final var emitter = new JavaEmitter(tempDir, tempDir, Map.of(), DtoStyle.CLASSES, Set.of(Account.class.getName()));
        emitter.emit(registry);

        final var content = Files.readString(new File(tempDir, "net/generated/Account.java").toPath());
        Assertions.assertTrue(content.contains("public record Account(String username)"), "overridden dto should be emitted as a record");
        Assertions.assertFalse(content.contains("public String username;"), "overridden record dto should not expose public fields");
    }
}
