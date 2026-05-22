package net.optionfactory.spring.downstream.plugin.processing;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;

public class TestClasspathDependenciesScanner implements DependenciesScanner {

    private final MavenProject project;

    public TestClasspathDependenciesScanner(MavenProject project) {
        this.project = project;
    }

    @Override
    public URL[] scan() throws MojoExecutionException, ProjectBuildingException, MalformedURLException, IOException, DependencyResolutionRequiredException {
        final var urls = new ArrayList<URL>();
        for (final var element : project.getTestClasspathElements()) {
            urls.add(new File(element).toURI().toURL());
        }
        return urls.toArray(URL[]::new);
    }

}
