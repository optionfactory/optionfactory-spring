package net.optionfactory.spring.downstream.plugin.processing;

import io.github.classgraph.ClassGraph;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import java.io.File;

public class Processor {

    private final Log log;
    private final MavenProject project;
    private final File outputDir;
    private final AnnotatedMethodsScanner methods;
    private final PayloadsScanner payloads;
    private final TypesMapper types;
    private final SourcesGenerator sources;

    public Processor(Log log, MavenProject project, String sourcePackage, String targetPackage, String targetClientName) {
        this.log = log;
        this.project = project;
        this.outputDir = new File(project.getBuild().getDirectory(), "generated-sources/downstream");
        this.methods = new AnnotatedMethodsScanner();
        this.payloads = new PayloadsScanner(sourcePackage, targetClientName);
        this.types = new TypesMapper(targetPackage);
        this.sources = new SourcesGenerator(outputDir, project.getBasedir(), targetPackage);
    }

    public void process() throws Exception {
        try (final var scanResult = new ClassGraph()
                .overrideClassLoaders(this.getClass().getClassLoader())
                .enableMethodInfo()
                .enableAnnotationInfo()
                .scan()) {

            final var ams = methods.scan(scanResult);
            log.info("Discovered %s methods annotated with @Downstream.Method".formatted(ams.size()));

            final var ps = payloads.scan(ams);
            log.info("Discovered %s dtos and enums".formatted(ps.size()));

            final var mapped = types.map(ps);
            final var candidates = sources.generate(mapped);

            for (final var candidate : candidates) {
                log.info("Source code generation: %s: %s".formatted(candidate.generated() ? "CREATED" : "SKIPPED", candidate.name()));
            }
        }

        project.addCompileSourceRoot(outputDir.getAbsolutePath());
        log.info("Generated code added to the compile source root: " + outputDir.getAbsolutePath());
    }
}
