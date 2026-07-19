package net.optionfactory.spring.downstream.plugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.optionfactory.spring.downstream.plugin.core.GenerationPipeline;
import net.optionfactory.spring.downstream.plugin.discovery.Endpoints;
import net.optionfactory.spring.downstream.plugin.discovery.Payloads;
import net.optionfactory.spring.downstream.plugin.emit.java.JavaEmitter;
import net.optionfactory.spring.downstream.plugin.emit.java.JavaEmitter.DtoStyle;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "generate-dtos", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateDtosMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(required = true)
    private String sourceBasePackage;

    @Parameter(required = false)
    private String target;

    @Parameter(required = false)
    private String targetClientName;

    @Parameter(required = true)
    private String targetPackage;

    @Parameter
    private Map<String, String> translations = new HashMap<>();

    @Parameter(defaultValue = "NESTED", required = true)
    private Nesting nesting;

    @Parameter(defaultValue = "RECORDS", required = true)
    private DtoStyle outputStyle;

    @Parameter
    private Set<String> outputStyleOverrides = new HashSet<>();

    @Override
    public void execute() throws MojoExecutionException {
        try {
            final var suffix = Optional.ofNullable(target)
                    .or(() -> Optional.ofNullable(targetClientName))
                    .map(v -> "-" + v)
                    .orElse("");

            final var outputDir = new File(project.getBuild().getDirectory(), "generated-sources/downstream" + suffix);

            final var endpoints = new Endpoints(targetClientName);
            final var payloads = new Payloads(sourceBasePackage);
            final var emitter = new JavaEmitter(outputDir, project.getBasedir(), translations, outputStyle, outputStyleOverrides);

            final var exclusions = translations.keySet();

            final var pipeline = new GenerationPipeline(getLog(), endpoints, payloads, emitter, exclusions);
            pipeline.execute(targetPackage, nesting);

            project.addCompileSourceRoot(outputDir.getAbsolutePath());
            getLog().info("Generated code added to the compile source root: " + outputDir.getAbsolutePath());

        } catch (Exception e) {
            throw new MojoExecutionException("Downstream Java code generation failed", e);
        }
    }
}
