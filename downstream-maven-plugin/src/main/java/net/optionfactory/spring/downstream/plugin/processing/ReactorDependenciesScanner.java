package net.optionfactory.spring.downstream.plugin.processing;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;

public class ReactorDependenciesScanner implements DependenciesScanner {

    private final ProjectBuilder projectBuilder;
    private final MavenSession session;
    private final List<MavenProject> reactorProjects;
    private final String sourceArtifactId;

    public ReactorDependenciesScanner(ProjectBuilder projectBuilder, MavenSession session, List<MavenProject> reactorProjects, String sourceArtifactId) {
        this.projectBuilder = projectBuilder;
        this.session = session;
        this.reactorProjects = reactorProjects;
        this.sourceArtifactId = sourceArtifactId;
    }

    @Override
    public URL[] scan() throws MojoExecutionException, ProjectBuildingException, MalformedURLException, IOException, DependencyResolutionRequiredException {
        final List<URL> urls = new ArrayList<>();
        if (reactorProjects == null) {
            throw new MojoExecutionException("Reactor project is null while scanning for '%s'".formatted(sourceArtifactId));
        }
        for (final var reactorProject : reactorProjects) {

            if (!reactorProject.getArtifactId().equals(sourceArtifactId)) {
                continue;
            }
            final var targetScanDir = new File(reactorProject.getBuild().getOutputDirectory()).getCanonicalFile();
            urls.add(targetScanDir.toURI().toURL());

            final var request = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            request.setResolveDependencies(true);

            final var result = projectBuilder.build(reactorProject.getFile(), request);

            for (final var element : result.getProject().getCompileClasspathElements()) {
                urls.add(new File(element).toURI().toURL());
            }
            return urls.toArray(URL[]::new);
        }
        throw new MojoExecutionException("Could not find a module associated with the configured artifactId '%s'".formatted(sourceArtifactId));

    }
}
