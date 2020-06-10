package net.optionfactory.spring.email.connector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.Result;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

public class PlaceboEmailConnector implements EmailConnector {

    private final Path sentPath;

    public PlaceboEmailConnector(Path sentPath) {
        Assert.isTrue(Files.isDirectory(sentPath), String.format("%s is not a directory", sentPath));
        Assert.isTrue(Files.isWritable(sentPath), String.format("%s is not writeable", sentPath));
        this.sentPath = sentPath;
    }

    @Override
    public Result<Void> send(Resource resource) {
        final String filename = resource.getFilename() == null ? UUID.randomUUID().toString() + ".eml" : resource.getFilename();
        try(InputStream is = resource.getInputStream()){
            Files.copy(is, sentPath.resolve(filename));
            return Result.value(null);
        } catch (IOException ex) {
            return Result.error(Problem.of("MESSAGING_EXCEPTION", null, null, ex.getMessage()));
        }
    }

}
