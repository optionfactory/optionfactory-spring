package net.optionfactory.spring.upstream.rendering;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.springframework.core.io.InputStreamSource;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class XsltRedactor {

    private final Templates templates;

    public XsltRedactor(Templates templates) {
        this.templates = templates;
    }

    public String redact(InputStreamSource source) {
        try (final var is = source.getInputStream(); final var writer = new StringWriter()) {
            final var transformer = templates.newTransformer();
            transformer.setOutputProperty("omit-xml-declaration", "yes");
            transformer.transform(new StreamSource(is), new StreamResult(writer));
            return writer.toString();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (TransformerException ex) {
            throw new IllegalStateException(ex);
        }
    }


    public enum Factory {
        INSTANCE;
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
        public static final String XSL_NS_URI = "http://www.w3.org/1999/XSL/Transform";

        public XsltRedactor create(Map<String, String> namespaces, List<String> attributes, List<String> tags) {
            final var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            final Document document;
            try (var is = new ByteArrayInputStream(TEMPLATE.getBytes(StandardCharsets.UTF_8))) {
                document = factory.newDocumentBuilder().parse(is);
            } catch (IOException | ParserConfigurationException | SAXException ex) {
                throw new IllegalStateException(ex);
            }
            final var stylesheet = document.getDocumentElement();
            for (Map.Entry<String, String> namespace : namespaces.entrySet()) {
                stylesheet.setAttribute(String.format("xmlns:%s", namespace.getKey()), namespace.getValue());
            }
            for (String attribute : attributes) {
                final var ael = document.createElementNS(XSL_NS_URI, "xsl:attribute");
                ael.setAttribute("name", "{name()}");
                ael.setTextContent("redacted");
                final var template = document.createElementNS(XSL_NS_URI, "xsl:template");
                template.setAttribute("match", attribute);
                template.appendChild(ael);
                stylesheet.appendChild(template);
            }
            for (String tag : tags) {
                final var applyAttributes = document.createElementNS(XSL_NS_URI, "xsl:apply-templates");
                applyAttributes.setAttribute("select", "@*");
                final var copy = document.createElementNS(XSL_NS_URI, "xsl:copy");
                copy.appendChild(applyAttributes);
                copy.appendChild(document.createTextNode("redacted"));
                final var template = document.createElementNS(XSL_NS_URI, "xsl:template");
                template.setAttribute("match", tag);
                template.appendChild(copy);
                stylesheet.appendChild(template);
            }
            try {
                return new XsltRedactor(TransformerFactory.newInstance().newTemplates(new DOMSource(document)));
            } catch (TransformerConfigurationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
