package net.optionfactory.spring.downstream.plugin.gen;

import java.io.IOException;
import java.util.List;
import net.optionfactory.spring.downstream.plugin.processing.TypesRegistry;

public interface SourcesGenerator {
    
    public enum GeneratorType {
        JAVA, TYPESCRIPT;
    }

    public record GenerateOutcome(String name, boolean generated) {

    }    
    
    List<GenerateOutcome> generate(TypesRegistry types) throws IOException;
    
}
