package net.optionfactory.spring.downstream.plugin.processing;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.ProjectBuildingException;


public interface DependenciesScanner {

    URL[] scan() throws MojoExecutionException, ProjectBuildingException, MalformedURLException, IOException, DependencyResolutionRequiredException;
    
}
