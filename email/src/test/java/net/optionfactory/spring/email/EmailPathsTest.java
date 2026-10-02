package net.optionfactory.spring.email;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class EmailPathsTest {

    @TempDir
    Path tmp;

    @Test
    public void missingDirectoriesAreCreatedWithTheirParents() {
        final var paths = EmailPaths.provide(tmp.resolve("a/spool"), tmp.resolve("b/sent"), tmp.resolve("c/dead"));

        Assertions.assertTrue(Files.isDirectory(paths.spool()), "the spool directory is created");
        Assertions.assertTrue(Files.isDirectory(paths.sent()), "the sent directory is created");
        Assertions.assertTrue(Files.isDirectory(paths.dead()), "the dead directory is created");
    }

    @Test
    public void sentAndDeadAreOptional() {
        final var paths = EmailPaths.provide(tmp.resolve("spool"), null, null);

        Assertions.assertNull(paths.sent(), "without a sent directory, sent emails are not archived");
        Assertions.assertNull(paths.dead(), "without a dead directory, dead emails are not archived");
    }

    @Test
    public void aRegularFileInPlaceOfADirectoryIsRejected() throws Exception {
        final var file = Files.createFile(tmp.resolve("not-a-directory"));

        Assertions.assertThrows(UncheckedIOException.class, () -> EmailPaths.provide(file, null, null), "a spool that is a regular file fails at startup");
    }
}
