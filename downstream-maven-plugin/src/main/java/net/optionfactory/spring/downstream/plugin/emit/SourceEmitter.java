package net.optionfactory.spring.downstream.plugin.emit;

import java.util.List;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;

public interface SourceEmitter {

    record GenerateOutcome(String name, boolean generated) {

    }

    List<GenerateOutcome> emit(TypeRegistry registry) throws Exception;

}
