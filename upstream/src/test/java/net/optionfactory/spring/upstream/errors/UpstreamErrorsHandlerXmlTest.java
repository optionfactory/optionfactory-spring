package net.optionfactory.spring.upstream.errors;

import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import net.optionfactory.spring.upstream.soap.Schemas;
import net.optionfactory.spring.upstream.soap.SoapHeaderWriter;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter;
import net.optionfactory.spring.upstream.soap.calc.AddResponse;
import org.junit.Test;
import org.springframework.http.MediaType;

public class UpstreamErrorsHandlerXmlTest {

    @Test
    public void canMatchUsingXpath() {
        UpstreamBuilder
                .create(UpstreamErrorsSoapClient.class)
                .soap(SoapJaxbHttpMessageConverter.Protocol.SOAP_1_1, Schemas.NONE, SoapHeaderWriter.NONE, AddResponse.class)
                .requestFactoryMock(c -> {
                    c.response(MockClientHttpResponse.okUtf8(
                            MediaType.TEXT_XML,
                            """
                            <?xml version="1.0" encoding="utf-8"?>
                            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                                <soap:Body>
                                    <AddResponse xmlns="http://tempuri.org/">
                                        <AddResult>8</AddResult>
                                    </AddResponse>
                                </soap:Body>
                            </soap:Envelope>
                            """
                    ));
                })
                .restClient(r -> r.baseUrl("http://example.com"))
                .build()
                .callWithXpath();
    }

    @Test(expected = RestClientUpstreamException.class)
    public void mismatchUsingXpathYieldsException() {
        UpstreamBuilder
                .create(UpstreamErrorsSoapClient.class)
                .soap(SoapJaxbHttpMessageConverter.Protocol.SOAP_1_1, Schemas.NONE, SoapHeaderWriter.NONE, AddResponse.class)
                .requestFactoryMock(c -> {
                    c.response(MockClientHttpResponse.okUtf8(
                            MediaType.TEXT_XML,
                            """
                            <?xml version="1.0" encoding="utf-8"?>
                            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                                <soap:Body>
                                    <AddResponse xmlns="http://tempuri.org/">
                                        <AddResult>9</AddResult>
                                    </AddResponse>
                                </soap:Body>
                            </soap:Envelope>
                            """
                    ));
                })
                .restClient(r -> r.baseUrl("http://example.com"))
                .build()
                .callWithXpath();
    }

}
