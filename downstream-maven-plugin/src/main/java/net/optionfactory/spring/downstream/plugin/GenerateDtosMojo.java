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

/// The `generate-dtos` goal: generates Java DTOs and enums for the payloads of the
/// `@Downstream.Method` endpoints of a server, so that a client can call it with types of its own.
///
/// Runs in the client project, in the `generate-sources` phase by default. The server classes are
/// scanned from the plugin's own classpath, so the server artifact must be a dependency of the
/// plugin, not of the project. The sources are written to
/// `target/generated-sources/downstream-{target}` (`target` falling back to `targetClientName`;
/// with neither, the directory is just `downstream`), which is then added as a compile source root.
///
/// Parameters:
/// - `sourceBasePackage` (required): only payload types whose package starts with it are
///   generated; the endpoints themselves are scanned on the whole classpath;
/// - `targetPackage` (required): the package of every generated type;
/// - `targetClientName`: selects the endpoints whose `clients` list it, plus the ones listing no
///   client; without it every endpoint is selected;
/// - `target`: names the output directory, defaults to `targetClientName`;
/// - `nesting` (default `NESTED`): how nested payload types are named, see
///   `TypeRegistry.Nesting`;
/// - `outputStyle` (default `RECORDS`): `RECORDS` or `CLASSES` with public fields;
/// - `outputStyleOverrides`: binary names of the source classes generated with the other style;
/// - `translations`: source class binary name to replacement type (a class name, a primitive, or
///   an array of them). Translated types are not generated (the payloads reachable through their
///   fields still are), and fields of those types use the replacement, or its generated
///   counterpart when the replacement is itself a payload.
///
/// A type is skipped, and logged as such, when the project already has a source file for it under
/// `src/main/java`: writing it by hand replaces the generated one.
///
/// ```xml
/// <plugin>
///     <groupId>net.optionfactory.spring</groupId>
///     <artifactId>downstream-maven-plugin</artifactId>
///     <executions>
///         <execution>
///             <goals>
///                 <goal>generate-dtos</goal>
///             </goals>
///             <configuration>
///                 <sourceBasePackage>com.example.server</sourceBasePackage>
///                 <targetPackage>com.example.client.dtos</targetPackage>
///                 <targetClientName>my-client</targetClientName>
///                 <translations>
///                     <java.time.LocalDate>java.lang.String</java.time.LocalDate>
///                 </translations>
///             </configuration>
///         </execution>
///     </executions>
///     <dependencies>
///         <dependency>
///             <groupId>com.example</groupId>
///             <artifactId>server</artifactId>
///             <version>${project.version}</version>
///         </dependency>
///     </dependencies>
/// </plugin>
/// ```
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

    /// @throws MojoExecutionException wrapping any failure of the generation, e.g. a naming
    /// collision between two generated types or an i/o error
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
