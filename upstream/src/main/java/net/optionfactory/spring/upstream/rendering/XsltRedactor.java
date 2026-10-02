package net.optionfactory.spring.upstream.rendering;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import net.optionfactory.spring.marshaling.jaxb.Xml;
import org.springframework.core.io.InputStreamSource;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/// Compacts and redacts an xml body before it is logged, with an XSLT stylesheet.
///
/// The stylesheet built by [Factory#create(Map, Map, Map)] copies the document dropping the xml
/// declaration and the whitespace-only text nodes and normalizing the spaces of the remaining
/// text, then replaces the values of the attributes and the content of the elements its patterns
/// match.
///
/// The document is parsed namespace-aware, with doctype declarations rejected. A transformer is
/// created for every call, so a redactor can be shared between threads.
public class XsltRedactor {

    private final Templates templates;
    private final SAXParserFactory saxParserFactory;

    /// @param templates the compiled stylesheet to apply
    public XsltRedactor(Templates templates) {
        this.templates = templates;
        final var f = Xml.saxParserFactory();
        f.setNamespaceAware(true);
        this.saxParserFactory = f;
    }

    /// @param source the xml body
    /// @return the transformed document, without an xml declaration
    /// @throws IllegalStateException when the body is not well-formed xml, carries a doctype or
    /// cannot be transformed
    /// @throws java.io.UncheckedIOException when the body cannot be read
    public String redact(InputStreamSource source) {
        try (final var is = source.getInputStream(); final var writer = new StringWriter()) {
            final var reader = saxParserFactory.newSAXParser().getXMLReader();
            final var transformer = templates.newTransformer();
            transformer.setOutputProperty("omit-xml-declaration", "yes");
            transformer.transform(new SAXSource(reader, new InputSource(is)), new StreamResult(writer));
            return writer.toString();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (TransformerException | ParserConfigurationException | SAXException ex) {
            throw new IllegalStateException(ex);
        }
    }


    /// Builds [XsltRedactor]s from redaction rules.
    public enum Factory {
        /// The only instance.
        INSTANCE;
        /// The base stylesheet: an identity copy that strips whitespace-only text nodes and
        /// normalizes the spaces of the remaining text.
        public static final String TEMPLATE = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
            <xsl:output indent="no"/>
            <xsl:strip-space elements="*"/>
            <xsl:template match="@*|node()">
                <xsl:copy>
                    <xsl:apply-templates select="@*|node()"/>
                </xsl:copy>
            </xsl:template>
            <xsl:template match="text()">
                <xsl:value-of select="normalize-space(.)"/>
            </xsl:template>                                                                            
        </xsl:stylesheet>
        """;
        /// The XSLT namespace.
        public static final String XSL_NS_URI = "http://www.w3.org/1999/XSL/Transform";

        /// Adds a template to the base stylesheet for each rule, and compiles it.
        ///
        /// An attribute rule replaces the value of the matched attributes. A tag rule replaces the
        /// content (child elements included) of the matched elements with the text, keeping their
        /// attributes, which attribute rules still apply to. Replacements are literal text, not
        /// XSLT expressions. When patterns overlap the XSLT conflict resolution rules decide, and
        /// the rules have no meaningful order among themselves.
        ///
        /// @param namespaces the namespaces the patterns refer to, by prefix; a prefix matches the
        /// namespace uri, whatever prefix the document uses for it
        /// @param attributes the replacement value of the attributes each XSLT pattern matches,
        /// e.g. `@password`
        /// @param tags the replacement content of the elements each XSLT pattern matches, e.g.
        /// `//password`
        /// @return the redactor
        /// @throws IllegalStateException when the stylesheet does not compile, e.g. because of an
        /// invalid pattern or an undeclared prefix
        public XsltRedactor create(Map<String, String> namespaces, Map<String, String> attributes, Map<String, String> tags) {
            final var factory = Xml.documentBuilderFactory();
            factory.setNamespaceAware(true);
            final Document document;
            try (var is = new ByteArrayInputStream(TEMPLATE.getBytes(StandardCharsets.UTF_8))) {
                document = factory.newDocumentBuilder().parse(is);
            } catch (IOException | ParserConfigurationException | SAXException ex) {
                throw new IllegalStateException(ex);
            }
            final var stylesheet = document.getDocumentElement();
            for (var namespace : namespaces.entrySet()) {
                stylesheet.setAttribute(String.format("xmlns:%s", namespace.getKey()), namespace.getValue());
            }
            for (var attributeAndSub : attributes.entrySet()) {
                final var ael = document.createElementNS(XSL_NS_URI, "xsl:attribute");
                ael.setAttribute("name", "{name()}");
                ael.setTextContent(attributeAndSub.getValue());
                final var template = document.createElementNS(XSL_NS_URI, "xsl:template");
                template.setAttribute("match", attributeAndSub.getKey());
                template.appendChild(ael);
                stylesheet.appendChild(template);
            }
            for (var tagAndSub : tags.entrySet()) {
                final var applyAttributes = document.createElementNS(XSL_NS_URI, "xsl:apply-templates");
                applyAttributes.setAttribute("select", "@*");
                final var copy = document.createElementNS(XSL_NS_URI, "xsl:copy");
                copy.appendChild(applyAttributes);
                copy.appendChild(document.createTextNode(tagAndSub.getValue()));
                final var template = document.createElementNS(XSL_NS_URI, "xsl:template");
                template.setAttribute("match", tagAndSub.getKey());
                template.appendChild(copy);
                stylesheet.appendChild(template);
            }
            try {
                return new XsltRedactor(Xml.transformerFactory().newTemplates(new DOMSource(document)));
            } catch (TransformerConfigurationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
