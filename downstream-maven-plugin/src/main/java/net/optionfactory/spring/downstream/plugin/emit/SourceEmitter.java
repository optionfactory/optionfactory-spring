package net.optionfactory.spring.downstream.plugin.emit;

import java.util.List;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;

/// Writes the code for the types of a [TypeRegistry]: one implementation per output language.
public interface SourceEmitter {

    /// The outcome of the generation of one output unit.
    ///
    /// @param name what was generated, e.g. a path or a type name, as reported in the build log
    /// @param generated true when it was written, false when it was skipped
    record GenerateOutcome(String name, boolean generated) {

    }

    /// @param registry the types to generate and their names
    /// @return one outcome per output unit
    /// @throws Exception when the code cannot be written
    List<GenerateOutcome> emit(TypeRegistry registry) throws Exception;

}
