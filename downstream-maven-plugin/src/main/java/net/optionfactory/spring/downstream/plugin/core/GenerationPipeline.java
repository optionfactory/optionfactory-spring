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
/// The classpath scanned is the one of the class loader given to [#execute]: the plugin's own when
/// the server is a jar dependency of the plugin, or the one of [ServerClasses] when it is a war.
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

    /// @param classLoader the class loader holding the server, as classes, jars or wars
    /// @param targetPackage the package of the generated types, ignored by emitters without
    /// packages
    /// @param nesting how nested payload types are named
    /// @throws IllegalStateException when no endpoint is found, or when two payload types end up
    /// with the same name
    /// @throws Exception when the emitter fails
    public void execute(ClassLoader classLoader, String targetPackage, Nesting nesting) throws Exception {

        try (final var scanResult = new ClassGraph()
                .enableClassLoaders(classLoader)
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .scan()) {

            final var methods = endpoints.discover(scanResult, classLoader);
            log.info("Discovered %s methods annotated with @Downstream.Method".formatted(methods.size()));
            if (methods.isEmpty()) {
                throw new IllegalStateException("no method annotated with @Downstream.Method was found for this client: add the server (classes, jar or war) as a dependency of the plugin");
            }

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