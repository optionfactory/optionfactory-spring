package net.optionfactory.spring.downstream.plugin.emit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;

/// Writes the code for the types of a [TypeRegistry]: one implementation per output language.
public interface SourceEmitter {

    /// The outcome of the generation of one output unit.
    ///
    /// @param name what was generated, e.g. a path or a type name, as reported in the build log
    /// @param status what happened to its file
    record GenerateOutcome(String name, Status status) {

        /// What happened to the file of an output unit.
        public enum Status {
            /// The file did not exist and was written.
            CREATED,
            /// The file existed with a different content and was overwritten.
            UPDATED,
            /// The file existed with the same content and was left untouched.
            UNCHANGED,
            /// The output unit was not generated.
            SKIPPED;
        }
    }

    /// @param registry the types to generate and their names
    /// @return one outcome per output unit
    /// @throws Exception when the code cannot be written
    List<GenerateOutcome> emit(TypeRegistry registry) throws Exception;

    /// Writes `content` to `file` only when it differs from what the file holds, so that an
    /// unchanged file keeps its modification time and does not trigger recompilations or
    /// watchers. Missing parent directories are created.
    ///
    /// @param file the file to write
    /// @param content the full content of the file, written as UTF-8
    /// @return [GenerateOutcome.Status#CREATED], [GenerateOutcome.Status#UPDATED] or
    /// [GenerateOutcome.Status#UNCHANGED]
    /// @throws IOException when the file cannot be read or written
    static GenerateOutcome.Status writeIfChanged(Path file, String content) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return GenerateOutcome.Status.CREATED;
        }
        if (Files.readString(file, StandardCharsets.UTF_8).equals(content)) {
            return GenerateOutcome.Status.UNCHANGED;
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return GenerateOutcome.Status.UPDATED;
    }

}
