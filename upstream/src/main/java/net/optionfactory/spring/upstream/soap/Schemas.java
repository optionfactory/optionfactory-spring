package net.optionfactory.spring.upstream.soap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.springframework.core.io.InputStreamSource;
import org.xml.sax.SAXException;

public class Schemas {

    public final static Schema NONE = null;

    public static Schema load(InputStreamSource firstSchema, InputStreamSource... otherSchemas) {

        try {
            final SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            final var sources = Stream.concat(Stream.of(firstSchema), Stream.of(otherSchemas))
                    .map(Schemas::toSource)
                    .toArray(i -> new StreamSource[i]);

            return sf.newSchema(sources);
        } catch (SAXException ex) {
            throw new IllegalStateException("Cannot load schemas", ex);
        }
    }

    private static StreamSource toSource(InputStreamSource iss) {
        try {
            return new StreamSource(iss.getInputStream());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
