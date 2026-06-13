package net.optionfactory.spring.downstream.plugin.e2e;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.optionfactory.spring.downstream.plugin.core.GenerationPipeline;
import net.optionfactory.spring.downstream.plugin.discovery.Endpoints;
import net.optionfactory.spring.downstream.plugin.discovery.Payloads;
import net.optionfactory.spring.downstream.plugin.emit.java.JavaEmitter;
import net.optionfactory.spring.downstream.plugin.emit.java.JavaEmitter.DtoStyle;
import net.optionfactory.spring.downstream.plugin.emit.ts.TypeScriptEmitter;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.apache.maven.plugin.logging.SystemStreamLog;

public class TestPipeline {

    private final String sourcePackage;
    private final String targetPackage;
    private final String targetClient;

    public TestPipeline(String sourcePackage, String targetPackage, String targetClient) {
        this.sourcePackage = sourcePackage;
        this.targetPackage = targetPackage;
        this.targetClient = targetClient;
    }

    public String typescript(File tempDir, Nesting nesting, Map<String, String> translations, Map<String, String> aliases) throws Exception {
        final var endpoints = new Endpoints(targetClient);
        final var payloads = new Payloads(sourcePackage);
        final var emitter = new TypeScriptEmitter(tempDir, translations, aliases);

        final var pipeline = new GenerationPipeline(new SystemStreamLog(), endpoints, payloads, emitter);
        pipeline.execute(targetPackage, nesting);

        final File file = new File(tempDir, "spec.d.ts");
        return Files.readString(file.toPath());
    }

    public Map<String, String> java(File tempDir, Nesting nesting, DtoStyle style, Map<String, String> translations) throws Exception {
        final var endpoints = new Endpoints(targetClient);
        final var payloads = new Payloads(sourcePackage);
        final var emitter = new JavaEmitter(tempDir, tempDir, translations, style);
        final var pipeline = new GenerationPipeline(new SystemStreamLog(), endpoints, payloads, emitter);
        pipeline.execute(targetPackage, nesting);
        final var contents = new HashMap<String, String>();

        try (var paths = Files.walk(tempDir.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(Files::isReadable)
                    .map(Path::toFile)
                    .forEach(file -> {
                        try {
                            final var basePath = tempDir.toPath();
                            final var targetPath = file.toPath();
                            final var key = basePath.relativize(targetPath).toString();
                            contents.put(key, Files.readString(file.toPath()));
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    });
        }
        return contents;
    }

}
