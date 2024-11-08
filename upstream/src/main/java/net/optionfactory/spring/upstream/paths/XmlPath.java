package net.optionfactory.spring.upstream.paths;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import org.xml.sax.SAXException;

public class XmlPath {

    public static final MethodHandle XPATH_BOOLEAN_METHOD_HANDLE = xpathBooleanMethodHandle();
    private final DocumentBuilderFactory builderFactory;
    private final ResponseContext response;

    public XmlPath(ResponseContext response) {
        this.builderFactory = DocumentBuilderFactory.newInstance();
        this.response = response;
    }

    public boolean xpathBool(String path) throws IOException {
        try {
            final var expression = XPathFactory.newInstance().newXPath().compile(path);
            final var builder = builderFactory.newDocumentBuilder();
            try (final var is = response.body().forInspection(true).inputStream()) {
                final var document = builder.parse(is);
                final var result = expression.evaluate(document, XPathConstants.BOOLEAN);
                return (Boolean) result;
            }
        } catch (SAXException | ParserConfigurationException | XPathExpressionException |RuntimeException ex) {
            return false;
        }
    }

    private static MethodHandle xpathBooleanMethodHandle() {
        try {
            return MethodHandles.publicLookup().findVirtual(XmlPath.class, "xpathBool", MethodType.methodType(boolean.class, String.class));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static MethodHandle xpathBooleanBoundMethodHandle(ResponseContext response) {
        return XPATH_BOOLEAN_METHOD_HANDLE.bindTo(new XmlPath(response));
    }

}
