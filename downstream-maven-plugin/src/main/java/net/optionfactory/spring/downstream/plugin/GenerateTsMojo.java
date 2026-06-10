package net.optionfactory.spring.downstream.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.util.HashMap;
import java.util.Map;
import net.optionfactory.spring.downstream.plugin.gen.SourcesGenerator.GeneratorType;
import net.optionfactory.spring.downstream.plugin.processing.Processor;
import net.optionfactory.spring.downstream.plugin.processing.TypesMapper.Nesting;

@Mojo(name = "generate-ts", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateTsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(required = true)
    private String sourceBasePackage;

    @Parameter(required = true)
    private String targetClientName;

    @Parameter
    private Map<String, String> translations = new HashMap<>();

    @Parameter(defaultValue = "FLATTEN", required = true)
    private Nesting nesting;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            final var processor = new Processor(getLog(), project, sourceBasePackage, "", targetClientName, translations, GeneratorType.TYPESCRIPT, nesting, false);
            processor.process();
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Downstream code generation failed", e);
        }

    }

}
