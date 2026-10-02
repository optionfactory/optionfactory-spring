package net.optionfactory.spring.marshaling.jaxb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class XmlTest {

    @TempDir
    Path dir;

    private static final String XSL_NS = "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"";
    private static final String XS_NS = "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"";

    @Test
    public void documentBuilderFactoryParsesPlainDocuments() throws Exception {
        final var db = Xml.documentBuilderFactory().newDocumentBuilder();
        final var document = db.parse(new InputSource(new StringReader("<a>b</a>")));
        Assertions.assertEquals("b", document.getDocumentElement().getTextContent(), "a document without DOCTYPE is parsed as usual");
    }

    @Test
    public void documentBuilderFactoryRejectsDoctypeDeclarations() throws Exception {
        final var db = Xml.documentBuilderFactory().newDocumentBuilder();
        db.setErrorHandler(new DefaultHandler());
        final var xxe = new InputSource(new StringReader("<!DOCTYPE a [<!ENTITY x \"y\">]><a>&x;</a>"));
        Assertions.assertThrows(SAXParseException.class, () -> db.parse(xxe), "a DOCTYPE, and with it any entity declaration, must be rejected");
    }

    @Test
    public void saxParserFactoryRejectsDoctypeDeclarations() throws Exception {
        final var parser = Xml.saxParserFactory().newSAXParser();
        final var xxe = new ByteArrayInputStream("<!DOCTYPE a [<!ENTITY x \"y\">]><a>&x;</a>".getBytes(StandardCharsets.UTF_8));
        Assertions.assertThrows(SAXParseException.class, () -> parser.parse(xxe, new DefaultHandler()), "a DOCTYPE, and with it any entity declaration, must be rejected");
    }

    @Test
    public void transformerFactoryCompilesSelfContainedStylesheets() throws Exception {
        final var stylesheet = new StreamSource(new StringReader("<xsl:stylesheet version=\"1.0\" %s/>".formatted(XSL_NS)));
        Assertions.assertNotNull(Xml.transformerFactory().newTransformer(stylesheet), "a stylesheet without external references is compiled");
    }

    @Test
    public void transformerFactoryRefusesExternalStylesheets() throws IOException {
        final var imported = Files.writeString(dir.resolve("imported.xsl"), "<xsl:stylesheet version=\"1.0\" %s/>".formatted(XSL_NS));
        final var stylesheet = new StreamSource(new StringReader("<xsl:stylesheet version=\"1.0\" %s><xsl:import href=\"%s\"/></xsl:stylesheet>".formatted(XSL_NS, imported.toUri())));
        final var tf = Xml.transformerFactory();
        Assertions.assertThrows(TransformerConfigurationException.class, () -> tf.newTransformer(stylesheet), "importing a stylesheet must be refused, even from a local file");
    }

    @Test
    public void schemaFactoryLoadsSelfContainedSchemas() throws Exception {
        final var schema = new StreamSource(new StringReader("<xs:schema %s><xs:element name=\"b\" type=\"xs:string\"/></xs:schema>".formatted(XS_NS)));
        Assertions.assertNotNull(Xml.schemaFactory().newSchema(schema), "a schema without external references is loaded");
    }

    @Test
    public void schemaFactoryRefusesExternalSchemas() throws IOException {
        final var included = Files.writeString(dir.resolve("included.xsd"), "<xs:schema %s><xs:element name=\"b\" type=\"xs:string\"/></xs:schema>".formatted(XS_NS));
        final var schema = new StreamSource(new StringReader("<xs:schema %s><xs:include schemaLocation=\"%s\"/></xs:schema>".formatted(XS_NS, included.toUri())));
        final var sf = Xml.schemaFactory();
        Assertions.assertThrows(SAXParseException.class, () -> sf.newSchema(schema), "including a schema must be refused, even from a local file");
    }

    @Test
    public void eachCallYieldsANewFactory() {
        Assertions.assertNotSame(Xml.documentBuilderFactory(), Xml.documentBuilderFactory(), "factories are not thread-safe: they must not be shared");
    }
}
