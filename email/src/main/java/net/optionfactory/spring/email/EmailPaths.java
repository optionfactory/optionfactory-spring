package net.optionfactory.spring.email;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public class EmailPaths {

    public Path spool;
    @Nullable
    public Path sent;
    @Nullable
    public Path dead;

    public static EmailPaths provide(Path spool, @Nullable Path sent, @Nullable Path dead) throws IOException {
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
        final var ep = new EmailPaths();
        ep.spool = spool;
        ep.sent = sent;
        ep.dead = dead;
        return ep;
    }

}
