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
import net.optionfactory.spring.marshaling.jaxb.Xml;
import org.xml.sax.SAXException;

/// Evaluates an XPath against an xml response body; exposed to the response expressions as the
/// `#xpath_bool('<xpath>')` function.
///
/// The body is parsed without namespace awareness, so a prefix cannot be used in the expression:
/// prefixed nodes are matched with `local-name()` or `name()`, e.g.
/// `//*[local-name()='Fault']`. Doctype declarations are rejected. Any failure (an unavailable
/// body, a malformed document, an invalid expression) yields `false`, so that an expression never
/// fails on it.
///
/// ```java
/// @Upstream.ErrorOnResponse("#xpath_bool('//AddResult/text() != \"8\"')")
/// ```
public class XmlPath {

    /// The unbound [#xpathBool(String)] handle, from which [#xpathBooleanBoundMethodHandle] derives
    /// the expression function.
    public static final MethodHandle XPATH_BOOLEAN_METHOD_HANDLE = xpathBooleanMethodHandle();
    private final DocumentBuilderFactory builderFactory = Xml.documentBuilderFactory();
    private final ResponseContext response;

    /// @param response the response to inspect, whose body must be buffered
    public XmlPath(ResponseContext response) {
        this.response = response;
    }

    /// Parses the body again on every call.
    ///
    /// @param path the XPath, whose result is converted to a boolean by the XPath rules (a node set
    /// is true when not empty, a number when not zero, a string when not empty)
    /// @return the boolean result, false when the body or the expression cannot be evaluated
    /// @throws IOException when closing the body stream fails
    public boolean xpathBool(String path) throws IOException {
        try {
            final var expression = XPathFactory.newInstance().newXPath().compile(path);
            final var builder = builderFactory.newDocumentBuilder();
            try (final var is = response.body().forInspection(true).getInputStream()) {
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

    /// @param response the response to inspect
    /// @return a `(String) -> boolean` handle evaluating XPaths on the response, suitable for a SpEL
    /// function variable
    public static MethodHandle xpathBooleanBoundMethodHandle(ResponseContext response) {
        return XPATH_BOOLEAN_METHOD_HANDLE.bindTo(new XmlPath(response));
    }

}
