package net.optionfactory.spring.downstream.plugin.processing;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;

public class DownstreamProcessor {

    private final File outputDir;
    private final DependenciesScanner dependenciesScanner;
    private final DownstreamMethodsScanner methodsScanner;
    private final DownstreamDtosScanner dtosScanner;
    private final DownstreamMappingsResolver mappingsResolver;
    private final DownstreamSourcesGenerator sourcesGenerator;
    private final MavenProject project;
    private final Log log;

    public DownstreamProcessor(Log log, MavenProject project, DependenciesScanner scanner, String sourcePackage, String targetPackage, String targetClientName) {
        this.log = log;
        this.project = project;
        this.outputDir = new File(project.getBuild().getDirectory(), "generated-sources/downstream");
        this.dependenciesScanner = scanner;
        this.methodsScanner = new DownstreamMethodsScanner();
        this.dtosScanner = new DownstreamDtosScanner(sourcePackage, targetClientName);
        this.mappingsResolver = new DownstreamMappingsResolver(targetPackage);
        this.sourcesGenerator = new DownstreamSourcesGenerator(outputDir, project.getBasedir(), targetPackage);
    }

    public void process() throws MojoExecutionException, ProjectBuildingException, IOException, MalformedURLException, DependencyResolutionRequiredException {
        final var urls = dependenciesScanner.scan();
        try (final var classLoader = new URLClassLoader(urls, this.getClass().getClassLoader())) {

            final var methods = methodsScanner.scan(classLoader);
            log.info("Discovered %s methods annotated with @Downstream.Method".formatted(methods.size()));

            final var scanResult = dtosScanner.scan(methods);
            log.info("Discovered %s dtos and enums".formatted(scanResult.size()));

            final var resolved = mappingsResolver.resolve(scanResult);
            final var candidates = sourcesGenerator.generate(resolved);
            for (final var candidate : candidates) {
                log.info("Source code generation: %s: %s".formatted(candidate.generated() ? "CREATED" : "SKIPPED", candidate.name()));
            }
            project.addCompileSourceRoot(outputDir.getAbsolutePath());
            log.info("Generated code added to the compile source root: " + outputDir.getAbsolutePath());
        }
    }
}
