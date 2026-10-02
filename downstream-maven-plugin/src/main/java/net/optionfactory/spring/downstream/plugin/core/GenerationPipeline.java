package net.optionfactory.spring.downstream.plugin.core;

import io.github.classgraph.ClassGraph;
import net.optionfactory.spring.downstream.plugin.discovery.Endpoints;
import net.optionfactory.spring.downstream.plugin.discovery.Payloads;
import net.optionfactory.spring.downstream.plugin.emit.SourceEmitter;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.apache.maven.plugin.logging.Log;

import java.util.Set;
import java.util.stream.Collectors;

/// Runs one generation: scans the classpath for the endpoints, collects their payloads, names them
/// and hands them to an emitter.
///
/// The classpath scanned is the one of the class loader that loaded the plugin, which is where
/// the server classes are when the server is a dependency of the plugin.
public class GenerationPipeline {

    private final Log log;
    private final Endpoints endpoints;
    private final Payloads payloads;
    private final SourceEmitter emitter;
    private final Set<String> exclusions;

    /// @param log where the counts and the outcome of each generated type are reported
    /// @param endpoints selects the endpoints to generate for
    /// @param payloads collects the payload types of the endpoints
    /// @param emitter writes the generated code
    /// @param exclusions binary names of the payload types not to generate, typically the ones
    /// translated or aliased to other types
    public GenerationPipeline(Log log, Endpoints endpoints, Payloads payloads, SourceEmitter emitter, Set<String> exclusions) {
        this.log = log;
        this.endpoints = endpoints;
        this.payloads = payloads;
        this.emitter = emitter;
        this.exclusions = exclusions;
    }

    /// @param targetPackage the package of the generated types, ignored by emitters without
    /// packages
    /// @param nesting how nested payload types are named
    /// @throws IllegalStateException when two payload types end up with the same name
    /// @throws Exception when the emitter fails
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