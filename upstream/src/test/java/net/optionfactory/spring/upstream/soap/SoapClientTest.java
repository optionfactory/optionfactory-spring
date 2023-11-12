package net.optionfactory.spring.upstream.soap;

import net.optionfactory.spring.upstream.soap.calc.CalculatorClient;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.SOAPFault;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.log.UpstreamLogging;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import net.optionfactory.spring.upstream.soap.calc.Add;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException.InternalServerError;

public class SoapClientTest {

    @Test
    public void canDoSoap11Call() throws JAXBException {
        final var client = UpstreamBuilder.create(CalculatorClient.class)
                .requestFactory((UpstreamHttpInterceptor.InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                    final var h = new HttpHeaders();
                    h.setContentType(MediaType.TEXT_XML); //Content-Type=[text/xml; charset=utf-8
                    final var content = """
                                        <?xml version="1.0" encoding="utf-8"?>
                                        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                                            <soap:Body>
                                                <AddResponse xmlns="http://tempuri.org/">
                                                    <AddResult>8</AddResult>
                                                </AddResponse>
                                            </soap:Body>
                                        </soap:Envelope>
                                        """;
                    return new MockClientHttpResponse(HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), headers, new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
                })
                .soap(Protocol.SOAP_1_1, SoapHeaderWriter.NONE, Add.class)
                .restClient(r -> r.baseUrl("http://www.dneonline.com/calculator.asmx"))
                .intercept(new UpstreamLogging.Interceptor())
                .build();

        Add req = new Add();
        req.intA = 3;
        req.intB = 5;
        final var got = client.add(req);
        Assert.assertEquals(8, got.addResult);
    }

    @Test
    public void canDoSoap12Call() throws JAXBException {
        final var client = UpstreamBuilder
                .create(CalculatorClient.class)
                .requestFactory((UpstreamHttpInterceptor.InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                    final var h = new HttpHeaders();
                    h.setContentType(MediaType.valueOf("application/soap+xml; charset=utf-8"));
                    final var content = """
                                        <?xml version="1.0" encoding="utf-8"?>
                                        <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                                            <soap:Body>
                                                <AddResponse xmlns="http://tempuri.org/">
                                                    <AddResult>8</AddResult>
                                                </AddResponse>
                                            </soap:Body>
                                        </soap:Envelope>
                                        """;
                    return new MockClientHttpResponse(HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), headers, new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
                })
                .soap(Protocol.SOAP_1_2, SoapHeaderWriter.NONE, Add.class)
                .restClient(r -> r.baseUrl("http://www.dneonline.com/calculator.asmx"))
                .intercept(new UpstreamLogging.Interceptor())
                .build();

        Add req = new Add();
        req.intA = 3;
        req.intB = 5;
        final var got = client.add(req);
        Assert.assertEquals(8, got.addResult);
    }

    @Test
    public void canReadFault() throws JAXBException {
        final var client = UpstreamBuilder
                .create(CalculatorClient.class)
                .requestFactory((UpstreamHttpInterceptor.InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                    final var h = new HttpHeaders();
                    h.setContentType(MediaType.valueOf("text/xml;charset=utf-8"));
                    final var content = """
                                        <?xml version="1.0" encoding="utf-8"?>
                                        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                                            <soap:Body>
                                                <soap:Fault>
                                                    <faultcode>soap:Client</faultcode>
                                                    <faultstring>System.Web.Services.Protocols.SoapException: something went wrong</faultstring>
                                                    <detail />
                                                </soap:Fault>
                                            </soap:Body>
                                        </soap:Envelope>
                                        """;
                    return new MockClientHttpResponse(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), headers, new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
                })
                .soap(Protocol.SOAP_1_1, SoapHeaderWriter.NONE, Add.class)
                .restClient(r -> r.baseUrl("http://www.dneonline.com/calculator.asmx"))
                .intercept(new UpstreamLogging.Interceptor())
                .build();

        Add req = new Add();
        req.intA = 3;
        req.intB = 5;
        try {
            client.faultingAdd(req);
            Assert.fail("should not happen");
        } catch (InternalServerError ex) {
            SOAPFault o = ex.getResponseBodyAs(SOAPFault.class);
            Assert.assertEquals("soap:Client", o.getFaultCode());
        }
    }
}
