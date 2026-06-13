package net.optionfactory.spring.downstream.plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.optionfactory.spring.downstream.plugin.core.GenerationPipeline;
import net.optionfactory.spring.downstream.plugin.discovery.Endpoints;
import net.optionfactory.spring.downstream.plugin.discovery.Payloads;
import net.optionfactory.spring.downstream.plugin.emit.ts.TypeScriptEmitter;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "generate-ts", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateTsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(required = false)
    private File targetDirectory;

    @Parameter(required = true)
    private String sourceBasePackage;

    @Parameter(required = false)
    private String target;
    
    @Parameter(required = true)
    private String targetClientName;

    @Parameter
    private Map<String, String> translations = new HashMap<>();

    @Parameter
    private Map<String, String> typeAliases = new HashMap<>();

    @Parameter(defaultValue = "FLATTEN", required = true)
    private Nesting nesting;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            final var suffix = Optional.ofNullable(target)
                    .or(() -> Optional.ofNullable(targetClientName))
                    .map(v -> "-" + v)
                    .orElse("");

            final var outputDir = targetDirectory != null
                    ? project.getBasedir().toPath().resolve(targetDirectory.toPath()).toFile()
                    : new File(project.getBuild().getDirectory(), "generated-resources/downstream" + suffix);

            final var endpoints = new Endpoints(targetClientName);
            final var payloads = new Payloads(sourceBasePackage);
            final var emitter = new TypeScriptEmitter(outputDir, translations, typeAliases);

            final var pipeline = new GenerationPipeline(getLog(), endpoints, payloads, emitter);
            pipeline.execute("", nesting);

            getLog().info("Generated TypeScript definitions written to: " + outputDir.getAbsolutePath());

        } catch (Exception e) {
            throw new MojoExecutionException("Downstream TypeScript generation failed", e);
        }
    }
}