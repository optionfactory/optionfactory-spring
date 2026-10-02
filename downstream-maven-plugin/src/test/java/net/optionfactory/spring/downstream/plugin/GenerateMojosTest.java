package net.optionfactory.spring.downstream.plugin;

import java.io.File;
import java.util.Map;
import java.util.Set;
import net.optionfactory.spring.downstream.plugin.e2e.ScanTarget;
import net.optionfactory.spring.downstream.plugin.emit.java.JavaEmitter.DtoStyle;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GenerateMojosTest {

    private static MavenProject project(File basedir) {
        final var project = new MavenProject();
        project.setFile(new File(basedir, "pom.xml"));
        final var build = new Build();
        build.setDirectory(new File(basedir, "target").getAbsolutePath());
        project.setBuild(build);
        return project;
    }

    private static <T extends AbstractMojo> T configure(T mojo, Map<String, Object> parameters) throws Exception {
        for (final var e : parameters.entrySet()) {
            final var field = mojo.getClass().getDeclaredField(e.getKey());
            field.setAccessible(true);
            field.set(mojo, e.getValue());
        }
        return mojo;
    }

    @Test
    public void generateDtosWritesUnderATargetClientSuffixedDirectoryAndAddsItAsSourceRoot(@TempDir File basedir) throws Exception {
        final var project = project(basedir);
        configure(new GenerateDtosMojo(), Map.of(
                "project", project,
                "sourceBasePackage", ScanTarget.class.getPackageName(),
                "targetClientName", "test-client",
                "targetPackage", "net.generated",
                "nesting", Nesting.NESTED,
                "outputStyle", DtoStyle.RECORDS
        )).execute();

        final var outputDir = new File(basedir, "target/generated-sources/downstream-test-client");
        Assertions.assertTrue(new File(outputDir, "net/generated/User.java").exists(), "dtos are generated in generated-sources/downstream-{targetClientName}");
        Assertions.assertTrue(project.getCompileSourceRoots().contains(outputDir.getAbsolutePath()), "the output directory becomes a compile source root");
    }

    @Test
    public void generateDtosPrefersTheTargetToTheClientNameForTheDirectory(@TempDir File basedir) throws Exception {
        configure(new GenerateDtosMojo(), Map.of(
                "project", project(basedir),
                "sourceBasePackage", ScanTarget.class.getPackageName(),
                "target", "custom",
                "targetClientName", "test-client",
                "targetPackage", "net.generated",
                "nesting", Nesting.NESTED,
                "outputStyle", DtoStyle.RECORDS
        )).execute();

        Assertions.assertTrue(new File(basedir, "target/generated-sources/downstream-custom/net/generated/User.java").exists(), "target names the output directory when both are given");
    }

    @Test
    public void generateTsWritesUnderGeneratedResourcesByDefault(@TempDir File basedir) throws Exception {
        configure(new GenerateTsMojo(), Map.of(
                "project", project(basedir),
                "sourceBasePackage", ScanTarget.class.getPackageName(),
                "targetClientName", "test-client",
                "nesting", Nesting.FLATTEN
        )).execute();

        Assertions.assertTrue(new File(basedir, "target/generated-resources/downstream-test-client/spec.d.ts").exists(), "the spec is generated in generated-resources/downstream-{targetClientName}");
    }

    @Test
    public void generateTsResolvesARelativeTargetDirectoryAgainstTheProjectBasedir(@TempDir File basedir) throws Exception {
        configure(new GenerateTsMojo(), Map.of(
                "project", project(basedir),
                "targetDirectory", new File("src/types"),
                "sourceBasePackage", ScanTarget.class.getPackageName(),
                "targetClientName", "test-client",
                "nesting", Nesting.FLATTEN
        )).execute();

        Assertions.assertTrue(new File(basedir, "src/types/spec.d.ts").exists(), "a relative targetDirectory is resolved against the project basedir, not the working directory");
    }
}
