package net.optionfactory.spring.pdf.signing;

import org.springframework.core.io.FileSystemResource;

import java.io.FilterInputStream;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

public class TemporaryFileSystemResource extends FileSystemResource {

    private static final Cleaner CLEANER = Cleaner.create();

    private final AtomicBoolean shouldDelete = new AtomicBoolean(true);
    private final AtomicBoolean consumed = new AtomicBoolean(false);
    private final Cleaner.Cleanable cleanable;

    public TemporaryFileSystemResource(String prefix, String suffix) throws IOException {
        super(Files.createTempFile(prefix, suffix).toFile());
        this.cleanable = CLEANER.register(this, new FileDeleter(this.getFile().toPath(), shouldDelete));
    }

    public void discard() {
        cleanable.clean();
    }

    /**
     * Moves the temporary file to a permanent location.
     *
     * @param target The destination path.
     * @return A new FileSystemResource pointing to the permanent file.
     * @throws IOException If the move fails.
     */
    public FileSystemResource moveTo(Path target) throws IOException {
        if (!consumed.compareAndSet(false, true)) {
            throw new IllegalStateException("This TemporaryFileSystemResource has already been consumed and cannot be moved.");
        }
        Files.move(this.getFile().toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        shouldDelete.set(false);
        cleanable.clean();
        return new FileSystemResource(target);
    }

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
                    // ignored
                }
            }
        }
    }
}
