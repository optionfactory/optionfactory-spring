package net.optionfactory.spring.resources.jaxb;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.transform.stream.StreamSource;

public class XmlResources {

    public enum FallbackStrategy {
        TRY_DEFAULT, NONE;
    }

    private final JAXBContext jaxb;
    private final FallbackStrategy strategy;
    private final Class<?> baseClass;
    private final Path basePath;

    public XmlResources(JAXBContext jaxbContext, FallbackStrategy strategy, Class<?> baseClass, String basePath) {
        this.jaxb = jaxbContext;
        this.strategy = strategy;
        this.baseClass = baseClass;
        this.basePath = Path.of(basePath);
    }

    public static JAXBContext context(Class<?> clazz, Class<?>... more) {
     final var contextPath = Stream
                .concat(Stream.of(clazz), Stream.of(more))
                .map(k -> k.getPackageName())
                .collect(Collectors.joining(":"));
        try {
            return JAXBContext.newInstance(contextPath);
        } catch (JAXBException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public <T> T unmarshal(Class<T> type, String prefix, Object... parts) {
        try ( var is = inputStream(prefix, parts)) {
            final JAXBElement<T> el = jaxb.createUnmarshaller().unmarshal(new StreamSource(is), type);
            return el.getValue();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (JAXBException ex) {
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
