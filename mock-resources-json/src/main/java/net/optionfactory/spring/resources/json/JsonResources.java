package net.optionfactory.spring.resources.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsonResources {

    public enum FallbackStrategy {
        TRY_DEFAULT, NONE;
    }

    private final ObjectMapper om;
    private final FallbackStrategy strategy;
    private final Class<?> baseClass;
    private final Path basePath;

    public JsonResources(ObjectMapper om, FallbackStrategy strategy, Class<?> baseClass, String basePath) {
        this.om = om;
        this.strategy = strategy;
        this.baseClass = baseClass;
        this.basePath = Path.of(basePath);
    }

    public <T> T unmarshal(TypeReference<T> type, String prefix, Object... parts) {
        try ( var is = inputStream(prefix, parts)) {
            return om.readValue(is, type);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public <T> T unmarshal(Class<T> type, String prefix, Object... parts) {
        try ( var is = inputStream(prefix, parts)) {
            return om.readValue(is, type);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private InputStream inputStream(String prefix, Object... parts) {

        final var specificSuffix = Stream.of(parts).map(Object::toString).collect(Collectors.joining("_"));
        final var specificFilename = String.format("%s_%s.json", prefix, specificSuffix);
        final var specific = basePath.resolve(specificFilename).toString();
        final var specificIs = baseClass.getResourceAsStream(specific);
        if (specificIs != null) {
            return specificIs;
        }
        if (strategy == FallbackStrategy.NONE) {
            throw new IllegalStateException(String.format("no valid resources found for %s: %s", prefix, List.of(specific)));
        }
        final var genericFilename = String.format("%s_default.json", prefix);
        final var generic = basePath.resolve(genericFilename).toString();
        final var genericIs = baseClass.getResourceAsStream(generic);
        if (genericIs == null) {
            throw new IllegalStateException(String.format("no valid resources found for %s: %s", prefix, List.of(specific, generic)));
        }
        return genericIs;
    }

}
