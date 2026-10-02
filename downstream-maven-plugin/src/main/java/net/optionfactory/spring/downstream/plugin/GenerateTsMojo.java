package net.optionfactory.spring.downstream.plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

/// The `generate-ts` goal: generates a TypeScript declaration file (`spec.d.ts`) for the payloads
/// of the `@Downstream.Method` endpoints of a server.
///
/// Runs in the client project, in the `generate-sources` phase by default. The server classes are
/// scanned from the plugin's own classpath, so the server artifact must be a dependency of the
/// plugin. DTOs become interfaces (an `Optional` or `@Nullable` property becoming optional),
/// enums become unions of string literals, collections become arrays and maps become `Record`s.
/// Nested types are always declared top-level, under their `flatName`.
///
/// The file is written to `targetDirectory`, resolved against the project base directory when
/// relative, or by default to `target/generated-resources/downstream-{target}` (`target` falling
/// back to `targetClientName`). It is not added to the project resources.
///
/// Parameters:
/// - `sourceBasePackage` (required): only payload types whose package starts with it are
///   generated;
/// - `targetClientName` (required): selects the endpoints whose `clients` list it, plus the ones
///   listing no client;
/// - `target`: names the default output directory, defaults to `targetClientName`;
/// - `targetDirectory`: the output directory, overriding the default one;
/// - `nesting` (default `FLATTEN`): `FLATTEN` drops the outer type names, `NESTED` and
///   `PREFIXED` both prefix nested types with them;
/// - `translations`: source class binary name to replacement java type. The replacement becomes
///   the TypeScript type of a well-known java type (`string`, `number`, `boolean`, `any`,
///   `void`), the name of an aliased class, the generated name of a payload, an array of the
///   mapping of its component (`byte[]` becomes `number[]`), or else its simple name;
/// - `typeAliases`: source class binary name to TypeScript type, declared as
///   `export type {SimpleName} = {type};` and referenced by that simple name. An alias wins over
///   a translation of the same class.
///
/// Translated and aliased classes are not generated. A class that is neither a payload, an enum,
/// a well-known java type, translated nor aliased becomes `any`.
///
/// ```xml
/// <execution>
///     <goals>
///         <goal>generate-ts</goal>
///     </goals>
///     <configuration>
///         <sourceBasePackage>com.example.server</sourceBasePackage>
///         <targetClientName>web-frontend</targetClientName>
///         <targetDirectory>src/main/frontend/types</targetDirectory>
///         <typeAliases>
///             <java.time.LocalDate>string</java.time.LocalDate>
///         </typeAliases>
///     </configuration>
/// </execution>
/// ```
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

    /// @throws MojoExecutionException wrapping any failure of the generation, e.g. a naming
    /// collision between two generated types or an i/o error
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

            final var exclusions = Stream.concat(translations.keySet().stream(), typeAliases.keySet().stream())
                    .collect(Collectors.toSet());
            
            final var pipeline = new GenerationPipeline(getLog(), endpoints, payloads, emitter, exclusions);
            pipeline.execute("", nesting);

            getLog().info("Generated TypeScript definitions written to: " + outputDir.getAbsolutePath());

        } catch (Exception e) {
            throw new MojoExecutionException("Downstream TypeScript generation failed", e);
        }
    }
}
