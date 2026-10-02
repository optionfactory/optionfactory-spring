package net.optionfactory.spring.pdf.signing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TemporaryFileSystemResourceTest {

    @TempDir
    Path dir;

    private static TemporaryFileSystemResource withContent(String content) throws IOException {
        final var r = new TemporaryFileSystemResource("tfsr-test-", ".txt");
        Files.writeString(r.getFile().toPath(), content);
        return r;
    }

    @Test
    public void theFileIsCreatedWithPrefixAndSuffix() throws IOException {
        final var r = new TemporaryFileSystemResource("tfsr-test-", ".txt");
        try {
            Assertions.assertTrue(r.exists(), "the temporary file exists once the resource is created");
            Assertions.assertTrue(r.getFilename().startsWith("tfsr-test-") && r.getFilename().endsWith(".txt"), "the file name has the given prefix and suffix");
        } finally {
            r.discard();
        }
    }

    @Test
    public void closingTheStreamDeletesTheFile() throws IOException {
        final var r = withContent("hello");
        final var path = r.getFile().toPath();
        try (final var is = r.getInputStream()) {
            Assertions.assertEquals("hello", new String(is.readAllBytes(), StandardCharsets.UTF_8), "the stream reads the file content");
        }
        Assertions.assertFalse(Files.exists(path), "the file is deleted when its stream is closed");
    }

    @Test
    public void theResourceCanBeReadOnce() throws IOException {
        final var r = withContent("hello");
        r.getInputStream().close();
        Assertions.assertThrows(IllegalStateException.class, r::getInputStream, "a second stream is refused");
    }

    @Test
    public void discardDeletesTheFile() throws IOException {
        final var r = withContent("hello");
        r.discard();
        Assertions.assertFalse(r.exists(), "a discarded resource has no file");
        Assertions.assertDoesNotThrow(r::discard, "discarding twice is harmless");
    }

    @Test
    public void movingKeepsTheFileAtTheTarget() throws IOException {
        final var r = withContent("hello");
        final var source = r.getFile().toPath();
        final var target = dir.resolve("kept.txt");
        final var moved = r.moveTo(target);
        r.discard();
        Assertions.assertFalse(Files.exists(source), "the temporary file is moved away");
        Assertions.assertEquals("hello", Files.readString(target), "the content is at the target, also after discarding the temporary resource");
        Assertions.assertEquals(target.toFile(), moved.getFile(), "the returned resource points to the target");
    }

    @Test
    public void movingOverwritesAnExistingTarget() throws IOException {
        final var target = Files.writeString(dir.resolve("existing.txt"), "old");
        withContent("new").moveTo(target);
        Assertions.assertEquals("new", Files.readString(target), "an existing target is replaced");
    }

    @Test
    public void aMovedResourceCannotBeReadNorMovedAgain() throws IOException {
        final var r = withContent("hello");
        r.moveTo(dir.resolve("kept.txt"));
        Assertions.assertThrows(IllegalStateException.class, r::getInputStream, "a moved resource is consumed");
        Assertions.assertThrows(IllegalStateException.class, () -> r.moveTo(dir.resolve("again.txt")), "a moved resource cannot be moved again");
    }

    @Test
    public void aReadResourceCannotBeMoved() throws IOException {
        final var r = withContent("hello");
        r.getInputStream().close();
        Assertions.assertThrows(IllegalStateException.class, () -> r.moveTo(dir.resolve("kept.txt")), "a read resource is consumed");
    }
}
