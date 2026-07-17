package net.optionfactory.spring.upstream.xml;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

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

}
