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
import net.optionfactory.spring.marshaling.jaxb.Xml;
import org.springframework.core.io.InputStreamSource;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/// Compiles the xml `Schema` a SOAP client validates its messages against.
///
/// ```java
/// final var schema = Schemas.fromWsdl(new ClassPathResource("/calculator/service.wsdl")).schema();
/// UpstreamBuilder.create(CalculatorClient.class)
///         .soap(Protocol.SOAP_1_1, schema, SoapHeaderWriter.NONE, Add.class)
///         ...
/// ```
///
/// Every document is parsed with doctype declarations rejected, and schema locations are never
/// fetched: each imported namespace must be provided, inline in the wsdl or as a companion xsd.
public class Schemas {

    /// No schema: messages are not validated. A readable `null` for the `soap` builder methods.
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

    /// The outcome of [Schemas#fromWsdl(InputStreamSource, InputStreamSource...)].
    ///
    /// @param schema the compiled schema
    /// @param protocols the SOAP versions the wsdl has bindings for, 1.1 first; empty when it has
    /// none
    public record SchemaAndProtocols(Schema schema, List<SoapJaxbHttpMessageConverter.Protocol> protocols) {

    }

    /// Compiles the schemas inlined in a wsdl, together with companion xsds, into a single
    /// `Schema`, and detects the SOAP versions the wsdl binds.
    ///
    /// The schemas are collected by target namespace, a companion xsd replacing an inline schema
    /// with the same one, and compiled imported namespaces first, so they can be given in any
    /// order. An import of a namespace that is not provided is not an ordering constraint, and is
    /// then left to the compilation.
    ///
    /// @param wsdlSource the wsdl, with its schemas in `wsdl:types`
    /// @param companionXsds standalone xsds the wsdl schemas import
    /// @return the schema and the protocols with a binding in the wsdl
    /// @throws IllegalStateException when a document cannot be read or parsed, the imports are
    /// circular, or the schema does not compile
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
            return new SchemaAndProtocols(Xml.schemaFactory().newSchema(orderedSources), protocols);
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

    /// Compiles standalone xsds into a single `Schema`.
    ///
    /// Unlike [#fromWsdl(InputStreamSource, InputStreamSource...)] the xsds are not sorted: one
    /// importing another's namespace must come after it.
    ///
    /// @param firstSchema the first xsd
    /// @param otherSchemas the following xsds, in dependency order
    /// @return the compiled schema
    /// @throws IllegalStateException when an xsd cannot be parsed or the schema does not compile
    /// @throws java.io.UncheckedIOException when an xsd cannot be opened
    public static Schema fromXsds(InputStreamSource firstSchema, InputStreamSource... otherSchemas) {
        try {
            final var sources = Stream.concat(Stream.of(firstSchema), Stream.of(otherSchemas))
                    .map(Schemas::toSource)
                    .toArray(StreamSource[]::new);

            return Xml.schemaFactory().newSchema(sources);
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
