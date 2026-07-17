package net.optionfactory.spring.marshaling.jaxb;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

public class Xml {

    public static DocumentBuilderFactory documentBuilderFactory() {
        try {
            final var f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f;
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
    }

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

    public static SchemaFactory schemaFactory() {
        try {
            final var sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return sf;
        } catch (SAXNotRecognizedException | SAXNotSupportedException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
