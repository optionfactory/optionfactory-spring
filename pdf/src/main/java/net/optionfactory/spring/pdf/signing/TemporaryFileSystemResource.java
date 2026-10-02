package net.optionfactory.spring.pdf.signing;

import org.springframework.core.io.FileSystemResource;

import java.io.FilterInputStream;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

/// A [FileSystemResource] over a temporary file that is consumed once, and deleted when no longer
/// needed.
///
/// The resource is consumed either by [#getInputStream()], whose stream deletes the file when
/// closed, or by [#moveTo(Path)], which keeps the file at a permanent location. A second attempt
/// to consume it throws an `IllegalStateException`. The file is also deleted by [#discard()], and,
/// as a safety net, when the resource is garbage collected without having been moved; relying on
/// the latter keeps the file on disk for an unpredictable time.
///
/// The other [FileSystemResource] methods, [#getFile()] included, are not guarded, and still point
/// to the temporary path once it is deleted or moved.
public class TemporaryFileSystemResource extends FileSystemResource {

    private static final Cleaner CLEANER = Cleaner.create();

    private final AtomicBoolean shouldDelete = new AtomicBoolean(true);
    private final AtomicBoolean consumed = new AtomicBoolean(false);
    private final Cleaner.Cleanable cleanable;

    /// Creates an empty file in the default temporary directory.
    ///
    /// @param prefix the prefix of the file name
    /// @param suffix the suffix of the file name, such as `.pdf`
    /// @throws IOException when the file cannot be created
    public TemporaryFileSystemResource(String prefix, String suffix) throws IOException {
        super(Files.createTempFile(prefix, suffix).toFile());
        this.cleanable = CLEANER.register(this, new FileDeleter(this.getFile().toPath(), shouldDelete));
    }

    /// Deletes the file, unless it was moved. Idempotent, and failures to delete are ignored.
    public void discard() {
        cleanable.clean();
    }

    /// Moves the file to a permanent location, replacing any file already there, and consumes the
    /// resource.
    ///
    /// @param target the destination path
    /// @return a resource over the moved file, which is no longer deleted
    /// @throws IOException when the move fails; the resource is consumed nevertheless, and the
    /// file is still deleted by [#discard()] or on garbage collection
    /// @throws IllegalStateException when the resource was already consumed
    public FileSystemResource moveTo(Path target) throws IOException {
        if (!consumed.compareAndSet(false, true)) {
            throw new IllegalStateException("This TemporaryFileSystemResource has already been consumed and cannot be moved.");
        }
        Files.move(this.getFile().toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        shouldDelete.set(false);
        cleanable.clean();
        return new FileSystemResource(target);
    }

    /// @return a stream over the file, which deletes the file when closed
    /// @throws IOException when the file cannot be opened
    /// @throws IllegalStateException when the resource was already consumed
    @Override
    public FilterInputStream getInputStream() throws IOException {
        if (!consumed.compareAndSet(false, true)) {
            throw new IllegalStateException("This TemporaryFileSystemResource has already been consumed and cannot be read multiple times.");
        }

        return new FilterInputStream(super.getInputStream()) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    cleanable.clean();
                }
            }
        };
    }

    private static class FileDeleter implements Runnable {

        private final Path path;
        private final AtomicBoolean shouldDelete;

        public FileDeleter(Path path, AtomicBoolean shouldDelete) {
            this.path = path;
            this.shouldDelete = shouldDelete;
        }

        @Override
        public void run() {
            if (shouldDelete.get()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
