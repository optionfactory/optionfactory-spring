package net.optionfactory.spring.downstream.plugin;

import java.util.HashMap;
import java.util.Map;
import net.optionfactory.spring.downstream.plugin.gen.JavaSourcesGenerator.JavaOutputStyle;
import net.optionfactory.spring.downstream.plugin.gen.SourcesGenerator.GeneratorType;
import net.optionfactory.spring.downstream.plugin.processing.Processor;
import net.optionfactory.spring.downstream.plugin.processing.TypesMapper.Nesting;
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
    private JavaOutputStyle outputStyle;

    
    
    @Override
    public void execute() throws MojoExecutionException {
        try {
            final var processor = new Processor(getLog(), project, sourceBasePackage, target, targetPackage, targetClientName, translations, GeneratorType.JAVA, nesting, outputStyle);
            processor.process();
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Downstream code generation failed", e);
        }
    }

}
