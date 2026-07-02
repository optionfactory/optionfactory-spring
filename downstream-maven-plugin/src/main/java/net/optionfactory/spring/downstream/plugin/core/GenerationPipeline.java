package net.optionfactory.spring.downstream.plugin.core;

import io.github.classgraph.ClassGraph;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.downstream.plugin.discovery.Endpoints;
import net.optionfactory.spring.downstream.plugin.discovery.Payloads;
import net.optionfactory.spring.downstream.plugin.emit.SourceEmitter;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.apache.maven.plugin.logging.Log;

public class GenerationPipeline {

    private final Log log;
    private final Endpoints endpoints;
    private final Payloads payloads;
    private final SourceEmitter emitter;
    private final Set<String> exclusions;
    
    public GenerationPipeline(Log log, Endpoints endpoints, Payloads payloads, SourceEmitter emitter, Set<String> exclusions) {
        this.log = log;
        this.endpoints = endpoints;
        this.payloads = payloads;
        this.emitter = emitter;
        this.exclusions = exclusions;        
    }

    public void execute(String targetPackage, Nesting nesting) throws Exception {

        try (final var scanResult = new ClassGraph()
                .overrideClassLoaders(this.getClass().getClassLoader())
                .enableMethodInfo()
                .enableAnnotationInfo()
                .scan()) {

            final var methods = endpoints.discover(scanResult);
            log.info("Discovered %s methods annotated with @Downstream.Method".formatted(methods.size()));

            final var candidates = payloads.discover(methods).stream()
                    .filter(c -> !exclusions.contains(c.getName()))
                    .collect(Collectors.toSet());            
            
            log.info("Discovered %s target payloads (dtos/enums)".formatted(candidates.size()));
            final var registry = new TypeRegistry(candidates, targetPackage, nesting);
            final var outcomes = emitter.emit(registry);

            for (final var outcome : outcomes) {
                log.info("Source code generation: %s: %s".formatted(outcome.generated() ? "CREATED" : "SKIPPED", outcome.name()));
            }
        }
    }
}
