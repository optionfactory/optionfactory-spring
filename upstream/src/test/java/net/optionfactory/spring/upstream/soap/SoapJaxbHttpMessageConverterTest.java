package net.optionfactory.spring.upstream.soap;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.SOAPFault;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import net.optionfactory.spring.upstream.soap.calc.Add;
import net.optionfactory.spring.upstream.soap.calc.AddResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;

public class SoapJaxbHttpMessageConverterTest {

    private static final String FAULT = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <soap:Fault>
                        <faultcode>soap:Server</faultcode>
                        <faultstring>boom</faultstring>
                    </soap:Fault>
                </soap:Body>
            </soap:Envelope>
            """;

    private static final String INVALID_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <AddResponse xmlns="http://tempuri.org/"><AddResult>not a number</AddResult></AddResponse>
                </soap:Body>
            </soap:Envelope>
            """;

    private static SoapJaxbHttpMessageConverter converter(SoapHeaderWriter headerWriter) throws JAXBException {
        final var schema = Schemas.fromXsds(new ClassPathResource("/calculator/schema.xsd"));
        return new SoapJaxbHttpMessageConverter(Protocol.SOAP_1_1, JAXBContext.newInstance(Add.class.getPackageName()), schema, headerWriter);
    }

    private static MockHttpInputMessage input(String body) {
        return new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void soap11SendsTheActionAsAQuotedHeader() {
        final var headers = Protocol.SOAP_1_1.headers(Optional.of("urn:say \"hi\" \\ bye"));
        Assertions.assertEquals(MediaType.TEXT_XML, headers.getContentType(), "SOAP 1.1 must be sent as text/xml");
        Assertions.assertEquals("\"urn:say \\\"hi\\\" \\\\ bye\"", headers.getFirst("SOAPAction"), "the action must be quoted, with quotes and backslashes escaped");
    }

    @Test
    public void soap11WithoutActionSendsNoActionHeader() {
        final var headers = Protocol.SOAP_1_1.headers(Optional.empty());
        Assertions.assertEquals(MediaType.TEXT_XML, headers.getContentType(), "SOAP 1.1 must be sent as text/xml");
        Assertions.assertFalse(headers.containsHeader("SOAPAction"), "without an action there must be no SOAPAction header");
    }

    @Test
    public void soap12SendsTheActionAsAMediaTypeParameter() {
        final var headers = Protocol.SOAP_1_2.headers(Optional.of("urn:add"));
        Assertions.assertTrue(MediaType.parseMediaType("application/soap+xml").includes(headers.getContentType()), "SOAP 1.2 must be sent as application/soap+xml");
        Assertions.assertEquals("\"urn:add\"", headers.getContentType().getParameter("action"), "the action must be a quoted media type parameter");
        Assertions.assertFalse(headers.containsHeader("SOAPAction"), "SOAP 1.2 must not send a SOAPAction header");
        Assertions.assertNull(Protocol.SOAP_1_2.headers(Optional.empty()).getContentType().getParameter("action"), "without an action there must be no action parameter");
    }

    @Test
    public void handlesXmlRootElementsAndFaults() throws JAXBException {
        final var converter = converter(SoapHeaderWriter.NONE);
        Assertions.assertTrue(converter.canRead(AddResponse.class, null), "an @XmlRootElement class must be readable");
        Assertions.assertTrue(converter.canRead(SOAPFault.class, null), "a SOAPFault must be readable");
        Assertions.assertFalse(converter.canRead(String.class, MediaType.TEXT_XML), "a class that is not an @XmlRootElement must not be readable");
        Assertions.assertTrue(converter.canWrite(Add.class, null), "an @XmlRootElement class must be writable");
        Assertions.assertFalse(converter.canWrite(SOAPFault.class, null), "a SOAPFault must not be writable");
    }

    @Test
    public void writesAnEnvelopeWithTheHeaderWriterOutput() throws JAXBException, IOException {
        final var converter = converter(new SoapHeaderWriter.WssUsernameToken("user", "pass"));
        final var request = new Add();
        request.intA = 1;
        request.intB = 2;
        final var out = new MockHttpOutputMessage();
        converter.write(request, Protocol.SOAP_1_1.mediaType, out);
        final var written = out.getBodyAsString(StandardCharsets.UTF_8);
        Assertions.assertTrue(written.startsWith("<?xml"), "the envelope must start with an xml declaration");
        Assertions.assertTrue(written.contains("<wsse:Username>user</wsse:Username>"), "the header writer must fill the SOAP header");
        Assertions.assertTrue(written.contains("intA>1</"), "the object must be marshalled in the body");
    }

    @Test
    public void readsTheFaultWhenAskedFor() throws JAXBException, IOException {
        final var fault = (SOAPFault) converter(SoapHeaderWriter.NONE).read(SOAPFault.class, input(FAULT));
        Assertions.assertEquals("boom", fault.getFaultString(), "the fault of the envelope must be returned");
    }

    @Test
    public void aBodyThatDoesNotValidateIsNotReadable() throws JAXBException {
        final var converter = converter(SoapHeaderWriter.NONE);
        Assertions.assertThrows(HttpMessageNotReadableException.class, () -> converter.read(AddResponse.class, input(INVALID_RESPONSE)), "a body violating the schema must not be readable");
    }

    @Test
    public void aMalformedEnvelopeIsNotReadable() throws JAXBException {
        final var converter = converter(SoapHeaderWriter.NONE);
        Assertions.assertThrows(HttpMessageNotReadableException.class, () -> converter.read(AddResponse.class, input("<not-an-envelope/>")), "a document that is not a SOAP envelope must not be readable");
    }
}
