package net.optionfactory.spring.downstream.plugin;

import net.optionfactory.spring.downstream.plugin.processing.Processor;
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

    @Parameter(required = true)
    private String targetClientName;

    @Parameter(required = true)
    private String targetPackage;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            final var processor = new Processor(getLog(), project, sourceBasePackage, targetPackage, targetClientName);
            processor.process();
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Downstream code generation failed", e);
        }
    }

}
