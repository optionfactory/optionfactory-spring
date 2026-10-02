package net.optionfactory.spring.email;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/// The directories an email goes through: written to `spool` by [EmailMarshaller#marshalToSpool],
/// then moved by the [EmailSender] to `sent` once delivered, or to `dead` once it has stayed
/// undelivered for too long.
///
/// `sent` and `dead` are optional: without them, a sent or dead email is deleted from the spool
/// rather than archived. The spool should belong to a single application, since the sender assumes
/// it owns it. All the directories should be on the same filesystem: emails are moved between them
/// with an atomic move, and a move that cannot be atomic fails, so the email is deleted rather than
/// archived.
///
/// Use [#provide] rather than the canonical constructor, which checks nothing.
///
/// @param spool where emails wait to be sent
/// @param sent where sent emails are archived, `null` to delete them
/// @param dead where emails that could not be sent in time are archived, `null` to delete them
public record EmailPaths(
        @NonNull
        Path spool,
        @Nullable
        Path sent,
        @Nullable
        Path dead) {

    /// Creates the missing directories (and their parents) and checks they are writable, so that a
    /// misconfiguration surfaces at startup rather than at the first email.
    ///
    /// @param spool where emails wait to be sent
    /// @param sent where sent emails are archived, `null` to delete them
    /// @param dead where undeliverable emails are archived, `null` to delete them
    /// @return the checked paths
    /// @throws java.io.UncheckedIOException when a directory cannot be created, e.g. because a
    /// regular file is in the way
    /// @throws IllegalArgumentException when a directory is not writable
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
