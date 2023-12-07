package net.optionfactory.spring.upstream.rendering;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import org.springframework.http.MediaType;

public class BodyRendering {

    public enum Strategy {
        SIZE,
        ABBREVIATED,
        ABBREVIATED_ONELINE,
        ABBREVIATED_COMPACT;
    }

    private static final String COMPACTING_XML_STYLESHEET = """
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
        </xsl:stylesheet>""";

    private static final Templates COMPACTING_XML_TEMPLATE = createTemplate();

    private static Templates createTemplate() {
        try (final var xsltReader = new StringReader(COMPACTING_XML_STYLESHEET)) {
            return TransformerFactory.newInstance().newTemplates(new StreamSource(xsltReader));
        } catch (TransformerConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String render(Strategy strategy, long contentLength, MediaType type, BodySource source, String infix, int maxSize) {
        return switch (strategy) {
            case SIZE ->
                String.format("size: %sB", contentLength);
            case ABBREVIATED ->
                abbreviated(source, infix, maxSize);
            case ABBREVIATED_COMPACT ->
                abbreviated(compact(source, type), infix, maxSize);
            case ABBREVIATED_ONELINE ->
                oneline(abbreviated(source, infix, maxSize));
        };
    }

    public static String oneline(String source) {
        return source.replaceAll("[\r\n]+", "");
    }

    public static String compact(BodySource source, MediaType type) {
        try {
            if (MediaType.parseMediaType("application/*+json").isCompatibleWith(type)) {
                return jsonCompact(source);
            }
            if (MediaType.parseMediaType("application/*+xml").isCompatibleWith(type) || MediaType.parseMediaType("text/*+xml").isCompatibleWith(type)) {
                return xsltCompact(source);
            }
        } catch (RuntimeException ex) {
            //fallback to oneline
        }
        return oneline(new String(source.bytes(), StandardCharsets.UTF_8));
    }

    public static String jsonCompact(BodySource source) {
        try (final var is = source.inputStream()) {
            return OBJECT_MAPPER.readValue(is, JsonNode.class).toString();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static String xsltCompact(BodySource source) {
        try (final var is = source.inputStream(); final var writer = new StringWriter()) {
            final var transformer = COMPACTING_XML_TEMPLATE.newTransformer();
            transformer.setOutputProperty("omit-xml-declaration", "yes");
            transformer.transform(new StreamSource(is), new StreamResult(writer));
            return writer.toString();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (TransformerException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String abbreviated(String source, String infix, int maxSize) {
        if (source.length() <= maxSize) {
            return source;
        }
        final int abbreviatedSize = maxSize / 2;

        final var prefix = source.substring(0, abbreviatedSize);
        final var suffix = source.substring(source.length() - abbreviatedSize, source.length());
        return prefix + infix + suffix;
    }

    public static String abbreviated(BodySource source, String infix, int maxSize) {
        final var bytes = source.bytes();
        if (bytes.length <= maxSize) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        final int abbreviatedSize = maxSize / 2;
        final var prefix = new String(bytes, 0, abbreviatedSize, StandardCharsets.UTF_8);
        final var suffix = new String(bytes, bytes.length - abbreviatedSize, abbreviatedSize, StandardCharsets.UTF_8);
        return prefix + infix + suffix;
    }

}
