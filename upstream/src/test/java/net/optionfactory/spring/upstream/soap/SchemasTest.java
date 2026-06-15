package net.optionfactory.spring.upstream.soap;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

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
        });

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
}
