package net.optionfactory.spring.downstream.plugin.e2e;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.tools.ToolProvider;
import net.optionfactory.spring.downstream.plugin.core.ServerClasses;
import net.optionfactory.spring.downstream.plugin.core.ServerClasses.Server;
import net.optionfactory.spring.downstream.plugin.emit.java.JavaEmitter.DtoStyle;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// A server packaged as a war, the way it reaches the plugin as a plugin dependency: its classes
/// under `WEB-INF/classes`, using in their signatures a type of a library, none of them on the
/// test classpath.
public class WarServerTest {

    private static final Map<String, String> SERVER = Map.of(
            "warsrv/Api.java", """
                    package warsrv;
                    import net.optionfactory.spring.downstream.Downstream;
                    public class Api {
                        @Downstream.Method(clients = {"war-client"})
                        public Reply post(Request request, warlib.Upload upload) {
                            return null;
                        }
                    }
                    """,
            "warsrv/Request.java", "package warsrv; public record Request(String name) {}",
            "warsrv/Reply.java", "package warsrv; public record Reply(int id) {}"
    );

    private static final Map<String, String> LIBRARY = Map.of(
            "warlib/Upload.java", "package warlib; public class Upload {}"
    );

    @Test
    public void generatesFromAWarReadInPlaceWithItsDependencies(@TempDir Path dir) throws Exception {
        final var server = server(dir);
        try (final var classes = ServerClasses.of(getClass().getClassLoader(), List.of(new Server(server.war(), List.of(server.libraryJar()))))) {
            final var contents = generate(dir, classes.classLoader());
            Assertions.assertTrue(contents.containsKey("net/generated/Request.java"), "a request type read from the war's WEB-INF/classes is generated, got " + contents.keySet());
            Assertions.assertTrue(contents.containsKey("net/generated/Reply.java"), "a response type read from the war's WEB-INF/classes is generated, got " + contents.keySet());
        }
    }

    /// A server not packaged yet reaches the plugin as its directory of classes, which Maven puts
    /// on the plugin's own class loader: loaded there, its classes would miss their dependencies.
    @Test
    public void generatesFromAServerNotPackagedYet(@TempDir Path dir) throws Exception {
        final var server = server(dir);
        try (final var plugin = new URLClassLoader(new URL[]{server.classes().toUri().toURL()}, getClass().getClassLoader());
                final var classes = ServerClasses.of(plugin, List.of(new Server(server.classes(), List.of(server.libraryClasses()))))) {
            Assertions.assertEquals(2, classes.classLoader().loadClass("warsrv.Api").getDeclaredMethods()[0].getParameterCount(), "a server class on the plugin's class loader too is loaded with its dependencies");
            final var contents = generate(dir, classes.classLoader());
            Assertions.assertTrue(contents.containsKey("net/generated/Request.java"), "a server still made of a directory of classes is read too, got " + contents.keySet());
        }
    }

    @Test
    public void loadsTheServerClassesAndTheTypesTheirSignaturesUse(@TempDir Path dir) throws Exception {
        final var server = server(dir);
        try (final var classes = ServerClasses.of(getClass().getClassLoader(), List.of(new Server(server.war(), List.of(server.libraryJar()))))) {
            final var api = classes.classLoader().loadClass("warsrv.Api");
            Assertions.assertEquals(2, api.getDeclaredMethods()[0].getParameterCount(), "a method using a type of a dependency is introspected");
            Assertions.assertSame(getClass().getClassLoader().loadClass("net.optionfactory.spring.downstream.Downstream$Method"),
                    classes.classLoader().loadClass("net.optionfactory.spring.downstream.Downstream$Method"),
                    "the annotation the plugin looks for is the plugin's own copy");
        }
    }

    @Test
    public void usesTheClassLoaderAsIsWithoutServers() {
        final var classLoader = getClass().getClassLoader();
        try (final var classes = ServerClasses.of(classLoader, List.of())) {
            Assertions.assertSame(classLoader, classes.classLoader(), "without servers the given class loader is used as is");
        }
    }

    @Test
    public void failsWhenNoEndpointIsFound(@TempDir Path dir) throws Exception {
        try (final var empty = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())) {
            final var ex = Assertions.assertThrows(IllegalStateException.class, () -> generate(dir, empty),
                    "a classpath without endpoints fails the generation instead of generating nothing");
            Assertions.assertTrue(ex.getMessage().contains("dependency of the plugin"), "the failure says where the server goes, got: " + ex.getMessage());
        }
    }

    private static Map<String, String> generate(Path dir, ClassLoader classLoader) throws Exception {
        final var pipeline = new TestPipeline("warsrv", "net.generated", "war-client");
        return pipeline.java(Files.createDirectories(dir.resolve("out")).toFile(), Nesting.NESTED, DtoStyle.RECORDS, Map.of(), classLoader);
    }

    private record Fixture(Path war, Path classes, Path libraryJar, Path libraryClasses) {

    }

    /// The server's war holds its classes only: its dependencies are resolved apart, as the plugin
    /// does, and are not read from `WEB-INF/lib`.
    private static Fixture server(Path dir) throws IOException {
        final var library = compile(dir.resolve("lib-classes"), LIBRARY, System.getProperty("java.class.path"));
        final var classes = compile(dir.resolve("server-classes"), SERVER, System.getProperty("java.class.path") + File.pathSeparator + library);
        final var jar = dir.resolve("warlib.jar");
        try (final var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            add(zip, library, "");
        }
        final var war = dir.resolve("server.war");
        try (final var zip = new ZipOutputStream(Files.newOutputStream(war))) {
            add(zip, classes, "WEB-INF/classes/");
        }
        return new Fixture(war, classes, jar, library);
    }

    private static Path compile(Path dir, Map<String, String> sources, String classpath) throws IOException {
        final var src = dir.resolve("src");
        final var out = dir.resolve("out");
        Files.createDirectories(out);
        final var files = new java.util.ArrayList<String>();
        for (final var source : sources.entrySet()) {
            final var file = src.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            files.add(file.toString());
        }
        final var args = new java.util.ArrayList<>(List.of("-proc:none", "-d", out.toString(), "-cp", classpath));
        args.addAll(files);
        final var result = ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new));
        Assertions.assertEquals(0, result, "the fixture sources compile");
        return out;
    }

    private static void add(ZipOutputStream zip, Path root, String prefix) throws IOException {
        try (final var paths = Files.walk(root)) {
            for (final var path : paths.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(new ZipEntry(prefix + root.relativize(path).toString().replace(File.separatorChar, '/')));
                zip.write(Files.readAllBytes(path));
                zip.closeEntry();
            }
        }
    }
}
