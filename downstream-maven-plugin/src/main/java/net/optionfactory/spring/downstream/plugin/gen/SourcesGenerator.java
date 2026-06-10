package net.optionfactory.spring.downstream.plugin.gen;

import net.optionfactory.spring.downstream.plugin.processing.TypesRegistry;

import java.io.IOException;
import java.util.List;

public interface SourcesGenerator {

    public enum GeneratorType {
        JAVA, TYPESCRIPT;
    }

    public record GenerateOutcome(String name, boolean generated) {

    }

    interface SourcesClassLoader {
        Class<?> load(String className);
    }

    List<GenerateOutcome> generate(TypesRegistry types, SourcesClassLoader cl) throws IOException;

}
