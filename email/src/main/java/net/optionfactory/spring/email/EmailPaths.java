package net.optionfactory.spring.email;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public record EmailPaths(
        @NonNull
        Path spool,
        @Nullable
        Path sent,
        @Nullable
        Path dead) {

    public static EmailPaths provide(Path spool, @Nullable Path sent, @Nullable Path dead) {
        try {
            Files.createDirectories(spool);
            Assert.isTrue(Files.isDirectory(spool), "email spool must be a directory");
            Assert.isTrue(Files.isWritable(spool), "email spool must be writable");
            if (sent != null) {
                Files.createDirectories(sent);
                Assert.isTrue(Files.isDirectory(sent), "email sent must be a directory");
                Assert.isTrue(Files.isWritable(sent), "email sent must be writable");
            }
            if (dead != null) {
                Files.createDirectories(dead);
                Assert.isTrue(Files.isDirectory(dead), "email dead must be a directory");
                Assert.isTrue(Files.isWritable(dead), "email dead must be writable");
            }
            return new EmailPaths(spool, sent, dead);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
