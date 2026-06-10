package net.optionfactory.spring.downstream.plugin.processing;

import io.github.classgraph.ClassGraph;
import net.optionfactory.spring.downstream.plugin.gen.JavaSourcesGenerator;
import net.optionfactory.spring.downstream.plugin.gen.JavaSourcesGenerator.JavaOutputStyle;
import net.optionfactory.spring.downstream.plugin.gen.SourcesGenerator;
import net.optionfactory.spring.downstream.plugin.gen.SourcesGenerator.GeneratorType;
import net.optionfactory.spring.downstream.plugin.gen.TypeScriptSourcesGenerator;
import net.optionfactory.spring.downstream.plugin.processing.TypesMapper.Nesting;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.Map;
import java.util.Optional;

public class Processor {

    private final Log log;
    private final GeneratorType genType;
    private final MavenProject project;
    private final File outputDir;
    private final AnnotatedMethodsScanner methods;
    private final PayloadsScanner payloads;
    private final TypesMapper types;
    private final SourcesGenerator generator;

    public Processor(Log log, MavenProject project, String sourcePackage, String target, File targetDirectory, String targetPackage, String targetClientName, Map<String, String> translations, GeneratorType genType, Nesting flattening, JavaOutputStyle javaOutputStyle) {
        final var suffix = Optional.ofNullable(target).or(() -> Optional.ofNullable(targetClientName)).map(v -> "-" + v).orElse(null);
        this.log = log;
        this.genType = genType;
        this.project = project;
        this.outputDir = targetDirectory != null
                ? project.getBasedir().toPath().resolve(targetDirectory.toPath()).toFile()
                : new File(project.getBuild().getDirectory(), "generated-%ssources/downstream%s".formatted(genType == GeneratorType.JAVA ? "" : "re", suffix));
        this.methods = new AnnotatedMethodsScanner();
        this.payloads = new PayloadsScanner(sourcePackage, targetClientName);
        this.types = new TypesMapper(targetPackage, flattening);
        this.generator = genType == GeneratorType.JAVA
                ? new JavaSourcesGenerator(outputDir, project.getBasedir(), targetPackage, translations, javaOutputStyle)
                : new TypeScriptSourcesGenerator(outputDir, translations);
    }

    public void process() throws Exception {
        final var classLoader = this.getClass().getClassLoader();
        try (final var scanResult = new ClassGraph()
                .overrideClassLoaders(classLoader)
                .enableMethodInfo()
                .enableAnnotationInfo()
                .scan()) {

            final var ams = methods.scan(scanResult);
            log.info("Discovered %s methods annotated with @Downstream.Method".formatted(ams.size()));

            final var ps = payloads.scan(ams);
            log.info("Discovered %s dtos and enums".formatted(ps.size()));

            final var mapped = types.map(ps);
            final var outcomes = generator.generate(mapped, (cn) -> scanResult.loadClass(cn, true));

            for (final var outcome : outcomes) {
                log.info("Source code generation: %s: %s".formatted(outcome.generated() ? "CREATED" : "SKIPPED", outcome.name()));
            }
        }

        if (genType == GeneratorType.JAVA) {
            project.addCompileSourceRoot(outputDir.getAbsolutePath());
            log.info("Generated code added to the compile source root: " + outputDir.getAbsolutePath());
        } else {
            log.info("Generated TypeScript definitions written to: " + outputDir.getAbsolutePath());
        }
    }
}
