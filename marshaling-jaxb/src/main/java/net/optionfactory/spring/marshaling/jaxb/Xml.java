package net.optionfactory.spring.marshaling.jaxb;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/// Factories for the JAXP parsers, transformers and schema loaders, hardened against XML external
/// entity (XXE) and related attacks.
///
/// Every factory refuses to reach outside the document it is handed: DOCTYPE declarations are
/// rejected outright by the parsers, and external DTDs, schemas and stylesheets cannot be loaded
/// through any protocol, `file:` included. Inline content is processed as usual. Apart from these
/// settings the factories keep the JAXP defaults, e.g. they are not namespace aware.
///
/// Each call returns a new factory: JAXP factories are not thread-safe, so the caller owns the
/// instance and configures it further as needed.
///
/// ```java
/// final var db = Xml.documentBuilderFactory().newDocumentBuilder();
/// final var document = db.parse(untrustedInput);
/// ```
public class Xml {

    /// A DOM factory rejecting DOCTYPE declarations, external DTDs and schemas, external entities
    /// and XInclude, and not expanding entity references.
    ///
    /// @return a new hardened factory
    /// @throws IllegalStateException when the JAXP implementation does not support one of the
    /// hardening features (the `http://apache.org/...` ones are specific to Xerces, the JDK default)
    /// @throws IllegalArgumentException when the JAXP implementation does not support the
    /// external-access attributes
    public static DocumentBuilderFactory documentBuilderFactory() {
        try {
            final var f = DocumentBuilderFactory.newInstance();
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f;
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// A SAX factory rejecting DOCTYPE declarations, external entities and XInclude.
    ///
    /// @return a new hardened factory
    /// @throws IllegalStateException when the JAXP implementation does not support one of the
    /// hardening features
    public static SAXParserFactory saxParserFactory() {
        try {
            final var f = SAXParserFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setXIncludeAware(false);
            return f;
        } catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// A transformer factory with secure processing enabled and no access to external DTDs or
    /// stylesheets: an `xsl:import` or `xsl:include` of a stylesheet outside the given source fails
    /// the creation of the transformer.
    ///
    /// @return a new hardened factory
    /// @throws IllegalStateException when the JAXP implementation does not support secure processing
    /// @throws IllegalArgumentException when the JAXP implementation does not support the
    /// external-access attributes
    public static TransformerFactory transformerFactory() {
        try {
            final var f = TransformerFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            return f;
        } catch (TransformerConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// A W3C XML Schema factory with secure processing enabled and no access to external DTDs or
    /// schemas: a schema that includes or imports another document by `schemaLocation` fails to
    /// load, so every schema must be self-contained or loaded from in-memory sources.
    ///
    /// @return a new hardened factory
    /// @throws IllegalStateException when the JAXP implementation does not support one of the
    /// hardening settings
    public static SchemaFactory schemaFactory() {
        try {
            final var sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return sf;
        } catch (SAXNotRecognizedException | SAXNotSupportedException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
