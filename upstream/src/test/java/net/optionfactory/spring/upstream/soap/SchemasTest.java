package net.optionfactory.spring.upstream.soap;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.xml.sax.SAXException;

public class SchemasTest {

    private static final String VALID_WSDL = """
        <?xml version="1.0" encoding="UTF-8"?>
        <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <wsdl:types>
                <xs:schema targetNamespace="http://example.com/root">
                    <xs:import namespace="http://example.com/leaf"/>
                    <xs:element name="Root" type="xs:string"/>
                </xs:schema>

                <xs:schema targetNamespace="http://example.com/leaf">
                    <xs:element name="Leaf" type="xs:string"/>
                </xs:schema>
            </wsdl:types>
        </wsdl:definitions>
    """.stripLeading();

    private static final String CIRCULAR_WSDL = """
        <?xml version="1.0" encoding="UTF-8"?>
        <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <wsdl:types>
                <xs:schema targetNamespace="http://example.com/a">
                    <xs:import namespace="http://example.com/b"/>
                </xs:schema>
                <xs:schema targetNamespace="http://example.com/b">
                    <xs:import namespace="http://example.com/a"/>
                </xs:schema>
            </wsdl:types>
        </wsdl:definitions>
    """.stripLeading();

    private static final String XSD_1 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="http://example.com/1">
            <xs:element name="One" type="xs:string"/>
        </xs:schema>
    """.stripLeading();

    private static final String XSD_2 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="http://example.com/2">
            <xs:element name="Two" type="xs:string"/>
        </xs:schema>
    """.stripLeading();

    @Test
    public void canLoadSortedSchemasFromWsdl() {
        final var schema = Schemas.fromWsdl(new ByteArrayResource(VALID_WSDL.getBytes()));
        Assertions.assertNotNull(schema, "expected schema to be successfully compiled from source Wsdl");
    }

    @Test
    public void loadingWsdlWithCircularDependenciesThrowsException() {
        final var exception = assertThrows(IllegalStateException.class, () -> {
            Schemas.fromWsdl(new ByteArrayResource(CIRCULAR_WSDL.getBytes()));
        }, "circular imports must be rejected");

        Assertions.assertTrue(exception.getCause().getMessage().contains("Circular schema dependency detected"), "expected to find a circular dependency");
    }

    @Test
    public void canLoadFromMultipleStandaloneXsds() {
        final var schema = Schemas.fromXsds(
                new ByteArrayResource(XSD_1.getBytes()),
                new ByteArrayResource(XSD_2.getBytes())
        );
        Assertions.assertNotNull(schema, "expected schema to be successfully compiled from source xsds");
    }

    @Test
    public void fromWsdlRejectsDoctypeBasedXxe() {
        final var xxe = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE wsdl:definitions [
              <!ENTITY xxe SYSTEM "file:///etc/passwd" >
            ]>
            <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <wsdl:types>
                    <xs:schema targetNamespace="http://example.com/root">
                        <xs:element name="Root" type="xs:string"/>
                    </xs:schema>
                </wsdl:types>
            </wsdl:definitions>
        """.stripLeading();
        Assertions.assertThrows(IllegalStateException.class, () ->
                Schemas.fromWsdl(new ByteArrayResource(xxe.getBytes())), "a wsdl with a doctype must be rejected");
    }

    private static final String BOUND_WSDL = """
        <?xml version="1.0" encoding="UTF-8"?>
        <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:xs="http://www.w3.org/2001/XMLSchema"
                          xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns:soap12="http://schemas.xmlsoap.org/wsdl/soap12/">
            <wsdl:types>
                <xs:schema targetNamespace="http://example.com/root" xmlns:leaf="http://example.com/leaf" elementFormDefault="qualified">
                    <xs:import namespace="http://example.com/leaf"/>
                    <xs:element name="Root">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element ref="leaf:Leaf"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
            </wsdl:types>
            <wsdl:binding name="b11"><soap:binding transport="http://schemas.xmlsoap.org/soap/http"/></wsdl:binding>
            <wsdl:binding name="b12"><soap12:binding transport="http://schemas.xmlsoap.org/soap/http"/></wsdl:binding>
        </wsdl:definitions>
    """.stripLeading();

    private static final String LEAF_XSD = """
        <?xml version="1.0" encoding="UTF-8"?>
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="http://example.com/leaf">
            <xs:element name="Leaf" type="xs:int"/>
        </xs:schema>
    """.stripLeading();

    private static void validate(Schema schema, String xml) throws SAXException, IOException {
        schema.newValidator().validate(new StreamSource(new StringReader(xml)));
    }

    @Test
    public void detectsTheBoundProtocols() {
        final var got = Schemas.fromWsdl(new ByteArrayResource(BOUND_WSDL.getBytes()), new ByteArrayResource(LEAF_XSD.getBytes()));
        Assertions.assertEquals(List.of(Protocol.SOAP_1_1, Protocol.SOAP_1_2), got.protocols(), "both bound SOAP versions must be detected, 1.1 first");
        Assertions.assertEquals(List.of(), Schemas.fromWsdl(new ByteArrayResource(VALID_WSDL.getBytes())).protocols(), "a wsdl without bindings must have no protocols");
    }

    @Test
    public void aCompanionXsdProvidesAnImportedNamespace() throws SAXException, IOException {
        final var schema = Schemas.fromWsdl(new ByteArrayResource(BOUND_WSDL.getBytes()), new ByteArrayResource(LEAF_XSD.getBytes())).schema();
        validate(schema, "<Root xmlns='http://example.com/root'><Leaf xmlns='http://example.com/leaf'>1</Leaf></Root>");
        Assertions.assertThrows(SAXException.class, () -> validate(schema, "<Root xmlns='http://example.com/root'><Leaf xmlns='http://example.com/leaf'>one</Leaf></Root>"), "an element violating the companion xsd must not validate");
    }

    @Test
    public void inlineSchemasAreCompiledWhateverTheirOrder() throws SAXException, IOException {
        final var schema = Schemas.fromWsdl(new ByteArrayResource(VALID_WSDL.getBytes())).schema();
        validate(schema, "<Root xmlns='http://example.com/root'>text</Root>");
        validate(schema, "<Leaf xmlns='http://example.com/leaf'>text</Leaf>");
        Assertions.assertThrows(SAXException.class, () -> validate(schema, "<Unknown xmlns='http://example.com/root'/>"), "an element no schema declares must not validate");
    }

    @Test
    public void fromXsdsRejectsAMalformedXsd() {
        Assertions.assertThrows(IllegalStateException.class, () -> Schemas.fromXsds(new ByteArrayResource("<xs:schema".getBytes())), "a malformed xsd must be rejected");
    }
}
