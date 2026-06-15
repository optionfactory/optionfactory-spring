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
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.springframework.core.io.InputStreamSource;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Schemas {

    public static final Schema NONE = null;

    private record SchemasAndImports(Map<String, Element> nsToSchema, Map<String, List<String>> nsToImports) {

        public Element schemaFor(String ns) {
            return nsToSchema.get(ns);
        }

        public List<String> importsFor(String ns) {
            return nsToImports.get(ns);
        }

        public Set<String> namespaces() {
            return nsToSchema.keySet();
        }

    }

    public static Schema fromWsdl(InputStreamSource wsdlSource) {
        try (InputStream is = wsdlSource.getInputStream()) {
            final var dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setNamespaceAware(true);
            final var doc = dbf.newDocumentBuilder().parse(is);
            final var schemaNodes = doc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "schema");
            final var sai = schemasAndImports(schemaNodes);
            final var orderedSources = orderedSources(sai);
            final var sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return sf.newSchema(orderedSources);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot load schemas from WSDL", ex);
        }
    }

    private static SchemasAndImports schemasAndImports(final NodeList schemaNodes) throws DOMException {
        final var result = new SchemasAndImports(new HashMap<>(), new HashMap<>());

        for (int i = 0; i != schemaNodes.getLength(); i++) {
            final var schemaElement = (Element) schemaNodes.item(i);
            final var tns = schemaElement.getAttribute("targetNamespace");
            result.nsToSchema().put(tns, schemaElement);

            final var imports = new ArrayList<String>();
            final var importNodes = schemaElement.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "import");

            for (int j = 0; j != importNodes.getLength(); j++) {
                final var importElement = (Element) importNodes.item(j);
                final var importedNamespace = importElement.getAttribute("namespace");
                if (importedNamespace != null && !importedNamespace.isEmpty()) {
                    imports.add(importedNamespace);
                }
            }
            result.nsToImports().put(tns, imports);
        }
        return result;
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

    public static Schema fromXsds(InputStreamSource firstSchema, InputStreamSource... otherSchemas) {

        try {
            final SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
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
