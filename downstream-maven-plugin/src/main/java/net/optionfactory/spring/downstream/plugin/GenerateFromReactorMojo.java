package net.optionfactory.spring.downstream.plugin;

import net.optionfactory.spring.downstream.plugin.processing.ReactorDependenciesScanner;
import net.optionfactory.spring.downstream.plugin.processing.DownstreamProcessor;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;

import java.util.List;
import javax.inject.Inject;

@Mojo(name = "generate-from-reactor", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateFromReactorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${reactorProjects}", readonly = true)
    private List<MavenProject> reactorProjects;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Inject
    private ProjectBuilder projectBuilder;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(required = true)
    private String sourceArtifactId;

    @Parameter(required = true)
    private String sourceBasePackage;

    @Parameter(required = true)
    private String targetClientName;

    @Parameter(required = true)
    private String targetPackage;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            final var scanner = new ReactorDependenciesScanner(projectBuilder, session, reactorProjects, sourceArtifactId);
            final var processor = new DownstreamProcessor(getLog(), project, scanner, sourceBasePackage, targetPackage, targetClientName);
            processor.process();
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Downstream code generation failed", e);
        }
    }

}
