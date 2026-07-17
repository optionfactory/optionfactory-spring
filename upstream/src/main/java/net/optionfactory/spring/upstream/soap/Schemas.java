package net.optionfactory.spring.upstream.soap;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import net.optionfactory.spring.upstream.xml.Xml;
import org.springframework.core.io.InputStreamSource;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class Schemas {

    public static final Schema NONE = null;

    private record SchemasAndImports(Map<String, Element> nsToSchema, Map<String, List<String>> nsToImports) {

        public Element schemaFor(String ns) {
            return nsToSchema.get(ns);
        }

        public List<String> importsFor(String ns) {
            return nsToImports.getOrDefault(ns, List.of());
        }

        public Set<String> namespaces() {
            return nsToSchema.keySet();
        }
    }

    public record SchemaAndProtocols(Schema schema, List<SoapJaxbHttpMessageConverter.Protocol> protocols) {

    }

    /// Compiles a single unified `Schema` validator from a primary WSDL resource and any optional 
    /// standalone companion XSD companion documents.
    ///
    /// Dependencies are automatically sorted.
    ///
    /// @param wsdlSource the primary WSDL stream input source containing core services and inline types
    /// @param companionXsds optional secondary standalone XSD streams containing cross-referenced definitions
    /// @return a configured, sequentially ordered `Schema` validation instance and detected supported protocols
    /// @throws IllegalStateException if an XML parsing error occurs, a circular schema dependency loop is found, or validator compilation fails
    public static SchemaAndProtocols fromWsdl(InputStreamSource wsdlSource, InputStreamSource... companionXsds) {
        try {
            final var dbf = Xml.documentBuilderFactory();
            dbf.setNamespaceAware(true);

            final var protocols = new ArrayList<SoapJaxbHttpMessageConverter.Protocol>();

            final var result = new SchemasAndImports(new HashMap<>(), new HashMap<>());
            try (InputStream is = wsdlSource.getInputStream()) {
                final var doc = dbf.newDocumentBuilder().parse(is);

                if (doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/soap/", "binding").getLength() > 0) {
                    protocols.add(SoapJaxbHttpMessageConverter.Protocol.SOAP_1_1);
                }
                if (doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/soap12/", "binding").getLength() > 0) {
                    protocols.add(SoapJaxbHttpMessageConverter.Protocol.SOAP_1_2);
                }

                final var schemaNodes = doc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "schema");
                for (int i = 0; i != schemaNodes.getLength(); i++) {
                    collectSchemaAndImports((Element) schemaNodes.item(i), result);
                }
            }

            for (final var xsdSource : companionXsds) {
                try (InputStream is = xsdSource.getInputStream()) {
                    final var doc = dbf.newDocumentBuilder().parse(is);
                    final var schemaElement = doc.getDocumentElement();
                    collectSchemaAndImports(schemaElement, result);
                }
            }
            final var orderedSources = orderedSources(result);
            final var sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return new SchemaAndProtocols(sf.newSchema(orderedSources), protocols);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot load schemas from WSDL", ex);
        }
    }

    private static void collectSchemaAndImports(Element schemaElement, SchemasAndImports accumulator) throws DOMException {
        final var tns = schemaElement.getAttribute("targetNamespace");
        accumulator.nsToSchema().put(tns, schemaElement);

        final var imports = new ArrayList<String>();
        final var importNodes = schemaElement.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "import");

        for (int j = 0; j != importNodes.getLength(); j++) {
            final var importElement = (Element) importNodes.item(j);
            final var importedNamespace = importElement.getAttribute("namespace");
            if (importedNamespace != null && !importedNamespace.isEmpty()) {
                imports.add(importedNamespace);
            }
        }
        accumulator.nsToImports().put(tns, imports);
    }

    private static Source[] orderedSources(SchemasAndImports sai) {
        final var orderedSources = new ArrayList<Source>();
        final var visited = new HashSet<String>();
        final var visiting = new HashSet<String>();
        for (final var namespace : sai.namespaces()) {
            resolveDependencies(namespace, sai, visited, visiting, orderedSources);
        }
        return orderedSources.toArray(Source[]::new);
    }

    private static void resolveDependencies(String namespace, SchemasAndImports sai, Set<String> visited, Set<String> visiting, List<Source> orderedSources) {
        if (visited.contains(namespace) || sai.schemaFor(namespace) == null) {
            return;
        }
        if (visiting.contains(namespace)) {
            throw new IllegalStateException("Circular schema dependency detected involving: " + namespace);
        }
        visiting.add(namespace);
        for (final var dep : sai.importsFor(namespace)) {
            resolveDependencies(dep, sai, visited, visiting, orderedSources);
        }
        visiting.remove(namespace);
        visited.add(namespace);
        orderedSources.add(new DOMSource(sai.schemaFor(namespace)));
    }

    /// Compiles a combined `Schema` context directly from an ordered varargs sequence of linear, standalone XSD resource streams.
    ///
    /// **Note**: This method assumes that the caller provides the files manually in their required step-by-step dependency sequence.
    ///
    /// @param firstSchema the first xsd source
    /// @param otherSchemas optional additional xsd sources
    /// @return a compiled `Schema` validation instance
    /// @throws IllegalStateException if a underlying SAX validation syntax error or environmental failure occurs
    public static Schema fromXsds(InputStreamSource firstSchema, InputStreamSource... otherSchemas) {
        try {
            final var sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            final var sources = Stream.concat(Stream.of(firstSchema), Stream.of(otherSchemas))
                    .map(Schemas::toSource)
                    .toArray(StreamSource[]::new);

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
